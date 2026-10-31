package com.phenom.selfservicebooking.booking.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

@Configuration
@Profile("!local")
@EnableConfigurationProperties(GoogleCalendarProperties.class)
public class GoogleCalendarConfig {

    private static final String TOKEN_USER_ID = "calendar-organizer";

    @Bean
    public Calendar googleCalendar(GoogleCalendarProperties properties) throws IOException, GeneralSecurityException {
        if (!StringUtils.hasText(properties.oauthClientPath())) {
            throw new IllegalStateException("google.calendar.oauth-client-path must point to the OAuth client JSON");
        }
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        GoogleClientSecrets clientSecrets;
        try (Reader reader = Files.newBufferedReader(Path.of(properties.oauthClientPath()))) {
            clientSecrets = GoogleClientSecrets.load(jsonFactory, reader);
        }

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                transport, jsonFactory, clientSecrets, List.of(CalendarScopes.CALENDAR))
                .setDataStoreFactory(new FileDataStoreFactory(Path.of(properties.tokensDir()).toFile()))
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(properties.oauthCallbackPort())
                .build();

        // Reuses the stored refresh token; only the first start opens the browser consent screen
        Credential credential;
        try {
            credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(TOKEN_USER_ID);
        } catch (NullPointerException e) {
            // The Google client NPEs when the callback carries ?error=... instead of a code
            throw new IllegalStateException("Google sign-in returned no authorization code: access was denied or"
                    + " cancelled. Make sure the signing-in account is a test user on the OAuth consent screen"
                    + " and click Allow.", e);
        }

        return new Calendar.Builder(transport, jsonFactory, credential)
                .setApplicationName(properties.applicationName())
                .build();
    }
}
