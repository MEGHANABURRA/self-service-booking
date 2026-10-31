package com.phenom.selfservicebooking.notification;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Objects;

public record BookingNotificationEvent(
        String eventId,
        BookingEventType eventType,
        String bookingId,
        String hostName,
        String hostEmail,
        String attendeeName,
        String attendeeEmail,
        Instant startsAt,
        Instant endsAt,
        Instant previousStartsAt,
        Instant previousEndsAt,
        String hostTimeZone,
        String attendeeTimeZone,
        URI interviewUrl,
        String hostLocale,
        String attendeeLocale) {

    public BookingNotificationEvent {
        requireText(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType is required");
        requireText(bookingId, "bookingId");
        requireText(hostName, "hostName");
        requireText(hostEmail, "hostEmail");
        requireText(attendeeName, "attendeeName");
        requireText(attendeeEmail, "attendeeEmail");
        Objects.requireNonNull(startsAt, "startsAt is required");
        Objects.requireNonNull(endsAt, "endsAt is required");
        requireText(hostTimeZone, "hostTimeZone");
        requireText(attendeeTimeZone, "attendeeTimeZone");
        Objects.requireNonNull(interviewUrl, "interviewUrl is required");
        hostLocale = normalizeLocale(hostLocale, "hostLocale");
        attendeeLocale = normalizeLocale(attendeeLocale, "attendeeLocale");
        ZoneId.of(hostTimeZone);
        ZoneId.of(attendeeTimeZone);
        String scheme = interviewUrl.getScheme();
        if (interviewUrl.getHost() == null
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("interviewUrl must be an absolute HTTP or HTTPS URL");
        }

        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        if (eventType == BookingEventType.BOOKING_RESCHEDULED) {
            Objects.requireNonNull(previousStartsAt, "previousStartsAt is required for reschedules");
            Objects.requireNonNull(previousEndsAt, "previousEndsAt is required for reschedules");
            if (!previousEndsAt.isAfter(previousStartsAt)) {
                throw new IllegalArgumentException("previousEndsAt must be after previousStartsAt");
            }
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static String normalizeLocale(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return Locale.ENGLISH.toLanguageTag();
        }

        try {
            Locale locale = new Locale.Builder().setLanguageTag(value).build();
            if (locale.getLanguage().isBlank()) {
                throw new IllegalArgumentException(fieldName + " must be a valid BCP 47 language tag");
            }
            return locale.toLanguageTag();
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid BCP 47 language tag", exception);
        }
    }
}
