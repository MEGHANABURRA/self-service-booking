package com.phenom.selfservicebooking.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

/**
 * Booking event published to the email notification topic; one message per host.
 * Times are UTC instants (e.g. 2026-11-02T06:30:00Z); previous* are set only for BOOKING_RESCHEDULED.
 */
public record BookingEventMessage(
        UUID eventId,
        BookingEventType eventType,
        UUID bookingId,
        String hostName,
        String hostEmail,
        String attendeeName,
        String attendeeEmail,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant startsAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant endsAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant previousStartsAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant previousEndsAt,
        String hostTimeZone,
        String attendeeTimeZone,
        String interviewUrl
) {
}
