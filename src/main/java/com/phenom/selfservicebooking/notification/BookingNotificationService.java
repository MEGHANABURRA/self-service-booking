package com.phenom.selfservicebooking.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class BookingNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingNotificationService.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final PhraseTranslator phraseTranslator;
    private final String fromAddress;

    public BookingNotificationService(
            JavaMailSender mailSender,
            SpringTemplateEngine templateEngine,
            PhraseTranslator phraseTranslator,
            @Value("${booking.notifications.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.phraseTranslator = phraseTranslator;
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
        ResourceBundle englishMessages = ResourceBundle.getBundle("email", Locale.ENGLISH);
        Locale locale = requestedLocale;
        Map<String, String> messages;
        try {
            messages = translateMessages(englishMessages, requestedLocale);
        } catch (RuntimeException exception) {
            LOGGER.warn("Email translation failed for locale {} ({}); using English text",
                    requestedLocale.toLanguageTag(), exception.getClass().getSimpleName());
            locale = Locale.ENGLISH;
            messages = englishMessages(englishMessages);
        }

        Context context = new Context(locale);
        context.setVariable("recipientName", recipientName);
        context.setVariable("bookingId", event.bookingId());
        context.setVariable("interviewUrl", event.interviewUrl().toASCIIString());
        context.setVariable("greeting", format(messages, "greeting", locale, recipientName));
        context.setVariable("eventSummary",
                format(messages, "event." + event.eventType().name().toLowerCase(Locale.ROOT) + "."
                        + recipientRole.name().toLowerCase(Locale.ROOT), locale, otherPartyName));
        context.setVariable("startLabel", messages.get("label.start"));
        context.setVariable("endLabel", messages.get("label.end"));
        context.setVariable("previousStartLabel", messages.get("label.previousStart"));
        context.setVariable("previousEndLabel", messages.get("label.previousEnd"));
        context.setVariable("newStartLabel", messages.get("label.newStart"));
        context.setVariable("newEndLabel", messages.get("label.newEnd"));
        context.setVariable("scheduledForLabel", messages.get("label.scheduledFor"));
        context.setVariable("interviewCallLabel", messages.get("label.interviewCall"));
        context.setVariable("joinInterviewLabel", messages.get("label.joinInterview"));
        context.setVariable("bookingReferenceLabel", messages.get("label.bookingReference"));
        context.setVariable("signoff", messages.get("signoff"));
        context.setVariable("signature", messages.get("signature"));

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
        String subject = messages.get("subject." + event.eventType().name().toLowerCase(Locale.ROOT));
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

    private Map<String, String> translateMessages(ResourceBundle englishMessages, Locale locale) {
        if ("en".equals(locale.getLanguage())) {
            return englishMessages(englishMessages);
        }

        Map<String, String> translated = new HashMap<>();
        for (String key : englishMessages.keySet()) {
            translated.put(key, phraseTranslator.translate(englishMessages.getString(key), locale));
        }
        return Map.copyOf(translated);
    }

    private static Map<String, String> englishMessages(ResourceBundle englishMessages) {
        Map<String, String> messages = new HashMap<>();
        for (String key : englishMessages.keySet()) {
            messages.put(key, englishMessages.getString(key));
        }
        return Map.copyOf(messages);
    }

    private static String format(Map<String, String> messages, String key, Locale locale, Object... arguments) {
        return new MessageFormat(messages.get(key), locale).format(arguments);
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
