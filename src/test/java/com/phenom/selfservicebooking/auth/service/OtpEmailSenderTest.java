package com.phenom.selfservicebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phenom.selfservicebooking.common.email.EmailDeliveryException;
import com.phenom.selfservicebooking.common.email.EmailMessage;
import com.phenom.selfservicebooking.common.email.EmailSender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Renders the real template and message bundles; records the email instead of sending it. */
class OtpEmailSenderTest {

    private final List<EmailMessage> sent = new ArrayList<>();
    private OtpEmailSender sender;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        sender = new OtpEmailSender(sent::add, engine, "no-reply@example.com");
    }

    @Test
    void sendsEnglishEmailWithCode() {
        sender.sendRegistrationOtp("john.doe@example.com", "042917", Duration.ofMinutes(10), Locale.US);

        assertThat(sent).hasSize(1);
        EmailMessage email = sent.get(0);
        assertThat(email.from()).isEqualTo("no-reply@example.com");
        assertThat(email.to()).isEqualTo("john.doe@example.com");
        assertThat(email.subject()).isEqualTo("Your verification code");
        assertThat(email.html()).contains("042917").contains("This code expires in 10 minutes.");
    }

    @Test
    void sendsHindiEmailWhenRequested() {
        sender.sendRegistrationOtp("john.doe@example.com", "042917", Duration.ofMinutes(10), Locale.forLanguageTag("hi"));

        EmailMessage email = sent.get(0);
        assertThat(email.subject()).isEqualTo("आपका सत्यापन कोड");
        assertThat(email.html()).contains("042917").contains("यह कोड 10 मिनट में समाप्त हो जाएगा।");
    }

    @Test
    void everyOtpMessageKeyExistsInBothLanguages() {
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.forLanguageTag("hi-IN")}) {
            ResourceBundle bundle = ResourceBundle.getBundle("email", locale);
            for (String key : new String[]{"otp.subject", "otp.greeting", "otp.intro", "otp.expiry", "otp.ignore"}) {
                assertThat(bundle.getString(key)).as(locale + " " + key).isNotBlank();
            }
        }
    }

    @Test
    void deliveryFailureIsPassedOn() {
        EmailSender failing = message -> {
            throw new EmailDeliveryException("SMTP down", null);
        };
        OtpEmailSender failingSender = new OtpEmailSender(failing, templateEngine(), "no-reply@example.com");

        assertThatThrownBy(() -> failingSender.sendRegistrationOtp(
                "john.doe@example.com", "042917", Duration.ofMinutes(10), Locale.ENGLISH))
                .isInstanceOf(EmailDeliveryException.class);
    }

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
