package com.phenom.selfservicebooking.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Stand-in for Google Calendar when running locally without Google credentials.
 */
@Service
@Profile("local")
public class LocalCalendarService implements CalendarService {

    private static final Logger log = LoggerFactory.getLogger(LocalCalendarService.class);

    @Override
    public CalendarEvent createEventWithMeetLink(LocalDateTime start, LocalDateTime end, List<String> attendeeEmails) {
        String eventId = "local-" + UUID.randomUUID();
        String meetLink = "https://meet.google.com/local-" + eventId.substring(6, 14);
        log.info("[local calendar] created event {} {} - {} for {}", eventId, start, end, attendeeEmails);
        return new CalendarEvent(eventId, meetLink);
    }

    @Override
    public void deleteEvent(String eventId) {
        log.info("[local calendar] deleted event {}", eventId);
    }

    @Override
    public void deleteEventQuietly(String eventId) {
        deleteEvent(eventId);
    }
}
