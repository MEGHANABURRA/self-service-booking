package com.phenom.selfservicebooking.common.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;

class JavaMailEmailSenderTest {

    private static final EmailMessage EMAIL = new EmailMessage(
            "no-reply@example.com", "john.doe@example.com", "आपका सत्यापन कोड", "<p>042917</p>");

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final JavaMailEmailSender sender = new JavaMailEmailSender(mailSender);

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendsHtmlEmail() throws Exception {
        sender.send(EMAIL);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();   // writes the headers, as the real SMTP sender does when sending
        assertThat(sent.getFrom()[0].toString()).isEqualTo("no-reply@example.com");
        assertThat(sent.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("john.doe@example.com");
        assertThat(sent.getSubject()).isEqualTo("आपका सत्यापन कोड");   // UTF-8 subject survives
        assertThat(sent.getContent().toString()).isEqualTo("<p>042917</p>");
        assertThat(sent.getContentType()).startsWith("text/html");
    }

    @Test
    void smtpFailureBecomesEmailDeliveryException() {
        doThrow(new MailAuthenticationException("535 auth failed")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(EMAIL))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("john.doe@example.com")
                .hasMessageContaining("535 auth failed");
    }
}
