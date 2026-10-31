package com.phenom.selfservicebooking.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.format.FormatStyle;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class BookingNotificationService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String fromAddress;

    public BookingNotificationService(
            JavaMailSender mailSender,
            SpringTemplateEngine templateEngine,
            @Value("${booking.notifications.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
    }

    public void sendNotifications(BookingNotificationEvent event) {
        sendToHost(event);
        sendToCandidate(event);
    }

    private void sendToHost(BookingNotificationEvent event) {
        send(event, RecipientRole.HOST, event.hostName(), event.hostEmail(), event.attendeeName(),
                ZoneId.of(event.hostTimeZone()), Locale.forLanguageTag(event.hostLocale()));
    }

    private void sendToCandidate(BookingNotificationEvent event) {
        send(event, RecipientRole.CANDIDATE, event.attendeeName(), event.attendeeEmail(), event.hostName(),
                ZoneId.of(event.attendeeTimeZone()), Locale.forLanguageTag(event.attendeeLocale()));
    }

    private void send(
            BookingNotificationEvent event,
            RecipientRole recipientRole,
            String recipientName,
            String recipientEmail,
            String otherPartyName,
            ZoneId recipientTimeZone,
            Locale requestedLocale) {
        Locale locale = supportedLocale(requestedLocale);
        ResourceBundle messages = ResourceBundle.getBundle("email", locale);
        Context context = new Context(locale);
        context.setVariable("recipientName", recipientName);
        context.setVariable("bookingId", event.bookingId());
        context.setVariable("interviewUrl", event.interviewUrl().toASCIIString());
        context.setVariable("greeting", format(messages, "greeting", recipientName));
        context.setVariable("eventSummary",
                format(messages, "event." + event.eventType().name().toLowerCase(Locale.ROOT) + "."
                        + recipientRole.name().toLowerCase(Locale.ROOT), otherPartyName));
        context.setVariable("startLabel", messages.getString("label.start"));
        context.setVariable("endLabel", messages.getString("label.end"));
        context.setVariable("previousStartLabel", messages.getString("label.previousStart"));
        context.setVariable("previousEndLabel", messages.getString("label.previousEnd"));
        context.setVariable("newStartLabel", messages.getString("label.newStart"));
        context.setVariable("newEndLabel", messages.getString("label.newEnd"));
        context.setVariable("scheduledForLabel", messages.getString("label.scheduledFor"));
        context.setVariable("interviewCallLabel", messages.getString("label.interviewCall"));
        context.setVariable("joinInterviewLabel", messages.getString("label.joinInterview"));
        context.setVariable("bookingReferenceLabel", messages.getString("label.bookingReference"));
        context.setVariable("signoff", messages.getString("signoff"));
        context.setVariable("signature", messages.getString("signature"));

        DateTimeFormatter slotFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(locale)
                .withZone(recipientTimeZone);
        context.setVariable("startsAt", slotFormat.format(event.startsAt()));
        context.setVariable("endsAt", slotFormat.format(event.endsAt()));
        if (event.previousStartsAt() != null) {
            context.setVariable(
                    "previousStartsAt",
                    slotFormat.format(event.previousStartsAt()));
        }
        if (event.previousEndsAt() != null) {
            context.setVariable(
                    "previousEndsAt",
                    slotFormat.format(event.previousEndsAt()));
        }

        String templateName = "email/" + recipientRole.templatePrefix + switch (event.eventType()) {
            case BOOKING_CONFIRMED -> "booking-confirmed";
            case BOOKING_RESCHEDULED -> "booking-rescheduled";
            case BOOKING_CANCELED -> "booking-canceled";
        };
        String subject = messages.getString("subject." + event.eventType().name().toLowerCase(Locale.ROOT));
        String html = templateEngine.process(templateName, context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException(
                    "Could not create " + event.eventType() + " email for booking " + event.bookingId()
                            + " and recipient " + recipientEmail,
                    exception);
        }
    }

    private static Locale supportedLocale(Locale requestedLocale) {
        return "hi".equals(requestedLocale.getLanguage()) ? Locale.forLanguageTag("hi-IN") : Locale.ENGLISH;
    }

    private static String format(ResourceBundle messages, String key, Object... arguments) {
        return new MessageFormat(messages.getString(key), messages.getLocale()).format(arguments);
    }

    private enum RecipientRole {
        HOST("host-"),
        CANDIDATE("candidate-");

        private final String templatePrefix;

        RecipientRole(String templatePrefix) {
            this.templatePrefix = templatePrefix;
        }
    }
}
