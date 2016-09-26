package org.nlab.smtp;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.*;
import org.nlab.smtp.pool.SmtpConnectionPool;
import org.nlab.smtp.transport.connection.ClosableSmtpConnection;
import org.nlab.smtp.transport.factory.SmtpConnectionFactory;
import org.nlab.smtp.transport.factory.SmtpConnectionFactoryBuilder;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FlushOnlyAfterCloseTest {

    // mailtrap.io - h1123863@mvrht.com - 123456

    private static final String MAILTRAP_TOKEN = "4b6149e563416d62a4deb9d467b8dd2d";
    private static final Integer MAILTRAP_INBOX_ID = 143004;
    private static final RequestSpecification REQUEST_SPEC;

    private SmtpConnectionPool connectionPool;

    static {
        RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder();
        requestSpecBuilder.setBaseUri("https://mailtrap.io");
        requestSpecBuilder.addHeader("Api-Token", MAILTRAP_TOKEN);
        requestSpecBuilder.setContentType(ContentType.JSON);
        requestSpecBuilder.addPathParam("inboxId", MAILTRAP_INBOX_ID);
        REQUEST_SPEC = requestSpecBuilder.build();
    }

    @Before
    public void before() {
        SmtpConnectionFactory connectionFactory = SmtpConnectionFactoryBuilder
                .newSmtpBuilder()
                .host("mailtrap.io")
                .port(25)
                .protocol("smtp")
                .username("ca83413abb171c")
                .password("3ebe29d15ef77c")
                .build();

        connectionPool = new SmtpConnectionPool(connectionFactory);

        RestAssured.with()
                   .spec(REQUEST_SPEC)
                   .patch("api/v1/inboxes/{inboxId}/clean")
                   .then()
                   .statusCode(200);

    }

    @Test
    public void shouldSendEmailPriorToConnectionRelease() throws Exception {

        CompletableFuture.runAsync(this::sendEmail).join();

        Assert.assertEquals(0, getInboxCount());

        connectionPool.close();

        Assert.assertEquals(1, getInboxCount());

    }

    private void sendEmail() {
        try (ClosableSmtpConnection transport = connectionPool.borrowObject()) {
            MimeMessage mimeMessage = new MimeMessage(transport.getSession());
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom("test@test.com");
            helper.setTo("test@test.com");
            helper.setSubject("Some subject");
            helper.setText("Some text");

            helper.addAttachment("attachment.txt", () -> {
                return new ByteArrayInputStream("Some attachment".getBytes());
            });

            transport.sendMessage(mimeMessage);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private int getInboxCount() {
        List inboxMessages = RestAssured
                .with()
                .spec(REQUEST_SPEC)
                .get("api/v1/inboxes/{inboxId}/messages")
                .then()
                .statusCode(200)
                .extract().body().as(List.class);
        return inboxMessages.size();
    }
}
