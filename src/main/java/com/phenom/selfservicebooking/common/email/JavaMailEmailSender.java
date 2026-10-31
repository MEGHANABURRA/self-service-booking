package com.phenom.selfservicebooking.common.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Sends email over SMTP using Spring's JavaMailSender (configured by the spring.mail.* properties). */
@Component
public class JavaMailEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    public JavaMailEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(EmailMessage email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(email.from());
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.html(), true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new EmailDeliveryException("Could not send email to " + email.to() + ": " + e.getMessage(), e);
        }
    }
}
