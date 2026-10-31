package com.phenom.selfservicebooking.notification;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class BookingNotificationServiceTest {

    @Test
    void sendsConfirmedEmailToBothParticipantsInTheirOwnTimezones() throws Exception {
        RecordingMailSender mailSender = new RecordingMailSender();
        BookingNotificationService service = createService(mailSender);
        BookingNotificationEvent event = new BookingNotificationEvent(
                "event-1",
                BookingEventType.BOOKING_CONFIRMED,
                "booking-1",
                "Host",
                "host@example.com",
                "Attendee",
                "attendee@example.com",
                Instant.parse("2026-11-02T05:15:00Z"),
                Instant.parse("2026-11-02T05:45:00Z"),
                null,
                null,
                "Asia/Kolkata",
                "Europe/London",
                URI.create("https://meet.example.com/booking-1"),
                null,
                null);

        service.sendNotifications(event);

        assertThat(mailSender.sentMessages).hasSize(2);
        assertThat(mailSender.sentMessages.get(0).getAllRecipients()[0].toString())
                .isEqualTo("host@example.com");
        assertThat(mailSender.sentMessages.get(0).getContent().toString())
                .contains("Nov 2, 2026", "10:45:00",
                        "Your interview with candidate Attendee",
                        "href=\"https://meet.example.com/booking-1\"");
        assertThat(mailSender.sentMessages.get(1).getAllRecipients()[0].toString())
                .isEqualTo("attendee@example.com");
        assertThat(mailSender.sentMessages.get(1).getContent().toString())
                .contains("Nov 2, 2026", "5:15:00",
                        "Your interview with Host",
                        "href=\"https://meet.example.com/booking-1\"");
    }

    @Test
    void usesRescheduledAndCanceledTemplates() throws Exception {
        RecordingMailSender mailSender = new RecordingMailSender();
        BookingNotificationService service = createService(mailSender);
        Instant start = Instant.parse("2026-11-02T05:15:00Z");
        Instant end = Instant.parse("2026-11-02T05:45:00Z");

        service.sendNotifications(new BookingNotificationEvent(
                "event-2",
                BookingEventType.BOOKING_RESCHEDULED,
                "booking-2",
                "Host",
                "host@example.com",
                "Attendee",
                "attendee@example.com",
                start.plusSeconds(3_600),
                end.plusSeconds(3_600),
                start,
                end,
                "Asia/Kolkata",
                "Europe/London",
                URI.create("https://meet.example.com/booking-2"),
                null,
                null));
        service.sendNotifications(new BookingNotificationEvent(
                "event-3",
                BookingEventType.BOOKING_CANCELED,
                "booking-3",
                "Host",
                "host@example.com",
                "Attendee",
                "attendee@example.com",
                start,
                end,
                null,
                null,
                "Asia/Kolkata",
                "Europe/London",
                URI.create("https://meet.example.com/booking-3"),
                null,
                null));

        assertThat(mailSender.sentMessages).hasSize(4);
        assertThat(mailSender.sentMessages.get(0).getSubject()).isEqualTo("Booking rescheduled");
        assertThat(mailSender.sentMessages.get(0).getContent().toString())
                .contains("Previous start:", "New start:", "interview with candidate Attendee",
                        "href=\"https://meet.example.com/booking-2\"");
        assertThat(mailSender.sentMessages.get(1).getContent().toString())
                .contains("Your interview with Host", "href=\"https://meet.example.com/booking-2\"");
        assertThat(mailSender.sentMessages.get(2).getSubject()).isEqualTo("Booking canceled");
        assertThat(mailSender.sentMessages.get(2).getContent().toString())
                .contains("has been canceled", "Was scheduled for:", "interview with candidate Attendee",
                        "href=\"https://meet.example.com/booking-3\"");
        assertThat(mailSender.sentMessages.get(3).getContent().toString())
                .contains("Your interview with Host", "href=\"https://meet.example.com/booking-3\"");
    }

    @Test
    void sendsEachParticipantEmailInTheirRequestedLanguage() throws Exception {
        RecordingMailSender mailSender = new RecordingMailSender();
        List<String> translatedPhrases = new ArrayList<>();
        BookingNotificationService service = createService(
                mailSender,
                (phrase, locale) -> {
                    assertThat(locale).isEqualTo(Locale.forLanguageTag("fr-FR"));
                    translatedPhrases.add(phrase);
                    return "FR: " + phrase;
                });
        service.sendNotifications(new BookingNotificationEvent(
                "event-fr",
                BookingEventType.BOOKING_CONFIRMED,
                "booking-fr",
                "Host",
                "host@example.com",
                "Candidate",
                "candidate@example.com",
                Instant.parse("2026-11-02T05:15:00Z"),
                Instant.parse("2026-11-02T05:45:00Z"),
                null,
                null,
                "Asia/Kolkata",
                "Asia/Kolkata",
                URI.create("https://meet.example.com/booking-fr"),
                "en",
                "fr-FR"));

        assertThat(mailSender.sentMessages).hasSize(2);
        assertThat(mailSender.sentMessages.get(0).getContent().toString())
                .contains("<html lang=\"en\">", "Hello Host", "Your interview with candidate");
        assertThat(mailSender.sentMessages.get(1).getContent().toString())
                .contains("<html lang=\"fr-FR\">", "FR: Hello Candidate,",
                        "FR: Your interview with Host is confirmed.")
                .contains("FR: Join the interview");
        assertThat(mailSender.sentMessages.get(1).getSubject()).isEqualTo("FR: Booking confirmed");
        assertThat(translatedPhrases).isNotEmpty()
                .allSatisfy(phrase -> assertThat(phrase)
                        .doesNotContain("Candidate", "Host", "candidate@example.com", "booking-fr",
                                "https://meet.example.com"));
    }

    @Test
    void fallsBackToEnglishWhenTranslationFails() throws Exception {
        RecordingMailSender mailSender = new RecordingMailSender();
        BookingNotificationService service = createService(
                mailSender,
                (phrase, locale) -> {
                    throw new IllegalStateException("Translation unavailable");
                });
        service.sendNotifications(new BookingNotificationEvent(
                "event-fallback",
                BookingEventType.BOOKING_CONFIRMED,
                "booking-fallback",
                "Host",
                "host@example.com",
                "Candidate",
                "candidate@example.com",
                Instant.parse("2026-11-02T05:15:00Z"),
                Instant.parse("2026-11-02T05:45:00Z"),
                null,
                null,
                "Asia/Kolkata",
                "Asia/Kolkata",
                URI.create("https://meet.example.com/booking-fallback"),
                "fr-FR",
                "en"));

        assertThat(mailSender.sentMessages).hasSize(2);
        assertThat(mailSender.sentMessages.get(0).getContent().toString())
                .contains("<html lang=\"en\">", "Your interview with candidate Candidate is confirmed.");
        assertThat(mailSender.sentMessages.get(1).getContent().toString())
                .contains("<html lang=\"en\">", "Your interview with Host is confirmed.");
    }

    private static BookingNotificationService createService(RecordingMailSender mailSender) {
        return createService(mailSender, (phrase, locale) -> phrase);
    }

    private static BookingNotificationService createService(
            RecordingMailSender mailSender,
            PhraseTranslator phraseTranslator) {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        templateEngine.setTemplateResolver(resolver);
        return new BookingNotificationService(mailSender, templateEngine, phraseTranslator, "no-reply@example.com");
    }

    private static class RecordingMailSender extends JavaMailSenderImpl {
        private final ArrayList<MimeMessage> sentMessages = new ArrayList<>();

        @Override
        public void send(MimeMessage message) {
            sentMessages.add(message);
        }
    }
}
