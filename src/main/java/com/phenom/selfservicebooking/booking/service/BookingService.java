package com.phenom.selfservicebooking.booking.service;

import com.phenom.selfservicebooking.booking.dto.BookingConfirmationRequest;
import com.phenom.selfservicebooking.booking.dto.BookingConfirmationResponse;
import com.phenom.selfservicebooking.booking.dto.BookingEventMessage;
import com.phenom.selfservicebooking.booking.dto.BookingEventType;
import com.phenom.selfservicebooking.booking.entity.Booking;
import com.phenom.selfservicebooking.booking.repository.BookingRepository;
import com.phenom.selfservicebooking.booking.service.CalendarService.CalendarEvent;
import com.phenom.selfservicebooking.candidate.entity.Candidate;
import com.phenom.selfservicebooking.candidate.repository.CandidateRepository;
import com.phenom.selfservicebooking.host.entity.Host;
import com.phenom.selfservicebooking.host.repository.HostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final HostRepository hostRepository;
    private final CandidateRepository candidateRepository;
    private final BookingRepository bookingRepository;
    private final CalendarService calendarService;
    private final KafkaTemplate<String, BookingEventMessage> kafkaTemplate;
    private final String emailNotificationTopic;
    private final int maxBookingsPerHostPerDay;
    private final ZoneId timeZone;

    public BookingService(HostRepository hostRepository,
                          CandidateRepository candidateRepository,
                          BookingRepository bookingRepository,
                          CalendarService calendarService,
                          KafkaTemplate<String, BookingEventMessage> kafkaTemplate,
                          @Value("${booking.kafka.email-notification-topic}") String emailNotificationTopic,
                          @Value("${booking.max-bookings-per-host-per-day}") int maxBookingsPerHostPerDay,
                          @Value("${google.calendar.time-zone}") ZoneId timeZone) {
        this.hostRepository = hostRepository;
        this.candidateRepository = candidateRepository;
        this.bookingRepository = bookingRepository;
        this.calendarService = calendarService;
        this.kafkaTemplate = kafkaTemplate;
        this.emailNotificationTopic = emailNotificationTopic;
        this.maxBookingsPerHostPerDay = maxBookingsPerHostPerDay;
        this.timeZone = timeZone;
    }

    public BookingConfirmationResponse createBookingConfirmation(BookingConfirmationRequest request) {
        validateStartNotInPast(request.startDateTime());
        List<Long> hostIds = request.hostIds().stream().distinct().toList();
        validateHostDailyLimit(hostIds, request.date());
        List<Host> hosts = fetchHosts(hostIds);
        Candidate candidate = fetchCandidate(request.candidateId());

        // 1. Block the hosts' (and candidate's) calendars and get the meet link
        List<String> attendeeEmails = new ArrayList<>(hosts.stream().map(Host::getEmailId).toList());
        attendeeEmails.add(candidate.getEmailId());
        CalendarEvent calendarEvent = calendarService.createEventWithMeetLink(
                request.startDateTime(), request.endDateTime(), attendeeEmails);

        // 2. Persist the booking; roll back the calendar event if that fails
        Booking booking;
        try {
            booking = bookingRepository.save(new Booking(request.candidateId(), hostIds, request.startDateTime(),
                    request.endDateTime(), calendarEvent.meetLink(), calendarEvent.eventId()));
        } catch (RuntimeException e) {
            calendarService.deleteEventQuietly(calendarEvent.eventId());
            throw e;
        }

        // 3. Notify candidate and hosts by email
        publishBookingEvents(BookingEventType.BOOKING_CONFIRMED, booking, hosts, candidate);

        return new BookingConfirmationResponse(
                booking.getId(),
                booking.getCandidateId(),
                booking.getHostIds(),
                booking.getBookingDate(),
                booking.getStartDateTime().toLocalTime(),
                booking.getEndDateTime().toLocalTime(),
                booking.getMeetLink()
        );
    }

    public void cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking not found: " + bookingId));

        // 1. Remove the event (and the invitation) from the attendees' calendars
        calendarService.deleteEvent(booking.getCalendarEventId());

        // 2. Soft delete the booking
        booking.markDeleted();
        bookingRepository.save(booking);

        // 3. Notify candidate and hosts that the booking is cancelled
        List<Host> hosts = hostRepository.findAllById(booking.getHostIds());
        Candidate candidate = candidateRepository.findById(booking.getCandidateId()).orElse(null);
        publishBookingEvents(BookingEventType.BOOKING_CANCELLED, booking, hosts, candidate);
    }

    // Request times are in the calendar time zone, so "now" must be too
    private void validateStartNotInPast(LocalDateTime startDateTime) {
        if (startDateTime.isBefore(LocalDateTime.now(timeZone))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "start_time must not be in the past (" + timeZone + ")");
        }
    }

    private void validateHostDailyLimit(List<Long> hostIds, LocalDate bookingDate) {
        List<Long> fullyBookedHostIds = hostIds.stream()
                .filter(hostId -> bookingRepository.countByHostIdAndBookingDate(hostId, bookingDate)
                        >= maxBookingsPerHostPerDay)
                .toList();
        if (!fullyBookedHostIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hosts " + fullyBookedHostIds
                    + " already have " + maxBookingsPerHostPerDay + " bookings on " + bookingDate);
        }
    }

    private List<Host> fetchHosts(List<Long> hostIds) {
        List<Host> hosts = hostRepository.findAllById(hostIds);
        if (hosts.size() != hostIds.size()) {
            Set<Long> foundIds = hosts.stream().map(Host::getId).collect(Collectors.toSet());
            List<Long> missingIds = hostIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hosts not found: " + missingIds);
        }
        return hosts;
    }

    private Candidate fetchCandidate(Long candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Candidate not found: " + candidateId));
    }

    // One event per host, so each host/attendee pair gets its own notification
    private void publishBookingEvents(BookingEventType eventType, Booking booking, List<Host> hosts,
                                      Candidate candidate) {
        Instant startsAt = booking.getStartDateTime().atZone(timeZone).toInstant();
        Instant endsAt = booking.getEndDateTime().atZone(timeZone).toInstant();
        for (Host host : hosts) {
            BookingEventMessage message = new BookingEventMessage(
                    UUID.randomUUID(),
                    eventType,
                    booking.getId(),
                    host.getName(),
                    host.getEmailId(),
                    candidate == null ? null : candidate.getName(),
                    candidate == null ? null : candidate.getEmailId(),
                    startsAt,
                    endsAt,
                    null,
                    null,
                    timeZoneOrDefault(host.getTimeZone()),
                    timeZoneOrDefault(candidate == null ? null : candidate.getTimeZone()),
                    booking.getMeetLink()
            );
            publish(booking.getId(), message);
        }
    }

    private String timeZoneOrDefault(String zone) {
        return zone != null ? zone : timeZone.getId();
    }

    private void publish(UUID bookingId, BookingEventMessage message) {
        kafkaTemplate.send(emailNotificationTopic, bookingId.toString(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} event {} for booking {}",
                                message.eventType(), message.eventId(), bookingId, ex);
                    }
                });
    }
}
