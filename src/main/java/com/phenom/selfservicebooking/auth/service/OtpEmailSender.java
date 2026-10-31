package com.phenom.selfservicebooking.auth.service;

import com.phenom.selfservicebooking.common.email.EmailMessage;
import com.phenom.selfservicebooking.common.email.EmailSender;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Locale;
import java.util.ResourceBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/** Emails the registration OTP, in English or Hindi (from the request's Accept-Language). */
@Component
public class OtpEmailSender {

    static final String TEMPLATE = "email/registration-otp";

    private final EmailSender emailSender;
    private final SpringTemplateEngine templateEngine;
    private final String fromAddress;

    public OtpEmailSender(
            EmailSender emailSender,
            SpringTemplateEngine templateEngine,
            @Value("${security.otp.from}") String fromAddress) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
    }

    public void sendRegistrationOtp(String to, String otp, Duration validity, Locale requestedLocale) {
        Locale locale = supportedLocale(requestedLocale);
        ResourceBundle messages = ResourceBundle.getBundle("email", locale);

        String html = templateEngine.process(TEMPLATE, buildContext(otp, validity, locale, messages));
        emailSender.send(new EmailMessage(fromAddress, to, messages.getString("otp.subject"), html));
    }

    static Context buildContext(String otp, Duration validity, Locale locale, ResourceBundle messages) {
        Context context = new Context(locale);
        context.setVariable("greeting", messages.getString("otp.greeting"));
        context.setVariable("intro", messages.getString("otp.intro"));
        context.setVariable("otp", otp);
        context.setVariable("expiry",
                new MessageFormat(messages.getString("otp.expiry"), locale).format(new Object[]{validity.toMinutes()}));
        context.setVariable("ignore", messages.getString("otp.ignore"));
        context.setVariable("signoff", messages.getString("signoff"));
        context.setVariable("signature", messages.getString("signature"));
        return context;
    }

    /** Same language rule as the booking emails: Hindi if asked for, otherwise English. */
    static Locale supportedLocale(Locale requestedLocale) {
        return requestedLocale != null && "hi".equals(requestedLocale.getLanguage())
                ? Locale.forLanguageTag("hi-IN")
                : Locale.ENGLISH;
    }
}
