package com.phenom.selfservicebooking.booking.service;

import java.time.LocalDateTime;
import java.util.List;

public interface CalendarService {

    CalendarEvent createEventWithMeetLink(LocalDateTime start, LocalDateTime end, List<String> attendeeEmails);

    /**
     * Deletes the event, which also removes it from every attendee's calendar.
     * An event that is already gone is treated as deleted.
     */
    void deleteEvent(String eventId);

    void deleteEventQuietly(String eventId);

    record CalendarEvent(String eventId, String meetLink) {
    }
}
