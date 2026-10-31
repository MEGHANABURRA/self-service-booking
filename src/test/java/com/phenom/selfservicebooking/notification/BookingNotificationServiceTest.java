package com.phenom.selfservicebooking.notification;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
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
        BookingNotificationService service = createService(mailSender);
        service.sendNotifications(new BookingNotificationEvent(
                "event-hi",
                BookingEventType.BOOKING_CONFIRMED,
                "booking-hi",
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
                URI.create("https://meet.example.com/booking-hi"),
                "en",
                "hi-IN"));

        assertThat(mailSender.sentMessages).hasSize(2);
        assertThat(mailSender.sentMessages.get(0).getContent().toString())
                .contains("Hello Host", "Your interview with candidate");
        assertThat(mailSender.sentMessages.get(1).getContent().toString())
                .contains("<html lang=\"hi-IN\">", "नमस्ते Candidate", "Host के साथ")
                .contains("साक्षात्कार में शामिल हों");
        assertThat(mailSender.sentMessages.get(1).getSubject()).isEqualTo("साक्षात्कार की पुष्टि");
    }

    private static BookingNotificationService createService(RecordingMailSender mailSender) {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        templateEngine.setTemplateResolver(resolver);
        return new BookingNotificationService(mailSender, templateEngine, "no-reply@example.com");
    }

    private static class RecordingMailSender extends JavaMailSenderImpl {
        private final ArrayList<MimeMessage> sentMessages = new ArrayList<>();

        @Override
        public void send(MimeMessage message) {
            sentMessages.add(message);
        }
    }
}
