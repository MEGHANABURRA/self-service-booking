package com.phenom.selfservicebooking.booking.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.phenom.selfservicebooking.booking.config.GoogleCalendarProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!local")
public class GoogleCalendarService implements CalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    private static final String GOOGLE_MEET = "hangoutsMeet";

    private final Calendar calendar;
    private final GoogleCalendarProperties properties;

    public GoogleCalendarService(Calendar calendar, GoogleCalendarProperties properties) {
        this.calendar = calendar;
        this.properties = properties;
    }

    @Override
    public CalendarEvent createEventWithMeetLink(LocalDateTime start, LocalDateTime end, List<String> attendeeEmails) {
        Event event = new Event()
                .setSummary(properties.eventSummary())
                .setStart(toEventDateTime(start))
                .setEnd(toEventDateTime(end))
                .setAttendees(attendeeEmails.stream().map(email -> new EventAttendee().setEmail(email)).toList())
                .setConferenceData(new ConferenceData().setCreateRequest(new CreateConferenceRequest()
                        .setRequestId(UUID.randomUUID().toString())
                        .setConferenceSolutionKey(new ConferenceSolutionKey().setType(GOOGLE_MEET))));
        try {
            // send-updates controls whether Google emails invites to the attendees (all | externalOnly | none)
            Event created = calendar.events().insert(properties.calendarId(), event)
                    .setConferenceDataVersion(1)
                    .setSendUpdates(properties.sendUpdates())
                    .execute();
            if (created.getHangoutLink() == null) {
                deleteEventQuietly(created.getId());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Calendar did not return a meet link");
            }
            return new CalendarEvent(created.getId(), created.getHangoutLink());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to create Google Calendar event", e);
        }
    }

    @Override
    public void deleteEvent(String eventId) {
        try {
            calendar.events().delete(properties.calendarId(), eventId)
                    .setSendUpdates(properties.sendUpdates())
                    .execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND.value() || e.getStatusCode() == HttpStatus.GONE.value()) {
                log.warn("Google Calendar event {} was already deleted", eventId);
                return;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to delete Google Calendar event", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to delete Google Calendar event", e);
        }
    }

    @Override
    public void deleteEventQuietly(String eventId) {
        try {
            deleteEvent(eventId);
        } catch (ResponseStatusException e) {
            log.error("Failed to delete Google Calendar event {}", eventId, e);
        }
    }

    private EventDateTime toEventDateTime(LocalDateTime dateTime) {
        long epochMillis = dateTime.atZone(properties.timeZone()).toInstant().toEpochMilli();
        return new EventDateTime()
                .setDateTime(new DateTime(epochMillis))
                .setTimeZone(properties.timeZone().getId());
    }
}
