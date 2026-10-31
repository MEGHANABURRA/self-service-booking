package com.phenom.selfservicebooking.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingNotificationListener {

    private final ObjectMapper objectMapper;
    private final BookingNotificationService notificationService;

    public BookingNotificationListener(
            ObjectMapper objectMapper,
            BookingNotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${booking.notifications.topic}")
    public void consume(String payload) {
        final BookingNotificationEvent event;
        try {
            event = objectMapper.readValue(payload, BookingNotificationEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid booking notification event payload", exception);
        }

        notificationService.sendNotifications(event);
    }
}
