package com.phenom.selfservicebooking.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "google.calendar")
public record GoogleCalendarProperties(
        String applicationName,
        String oauthClientPath,
        String tokensDir,
        int oauthCallbackPort,
        String calendarId,
        ZoneId timeZone,
        String eventSummary,
        String sendUpdates
) {
}
