package com.phenom.selfservicebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.phenom.selfservicebooking.auth.dto.SendOtpResponse;
import com.phenom.selfservicebooking.auth.entity.EmailOtp;
import com.phenom.selfservicebooking.auth.exception.InvalidOtpException;
import com.phenom.selfservicebooking.auth.exception.OtpRateLimitException;
import com.phenom.selfservicebooking.auth.repository.EmailOtpRepository;
import com.phenom.selfservicebooking.common.email.EmailDeliveryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceTest {

    private static final String SECRET = "kzIQVRLQUCxztBXOC02cGfZO2ZLbp53a3VACpgZITRA=";
    private static final String EMAIL = "john.doe@example.com";
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final Duration EXPIRATION = Duration.ofMinutes(10);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    @Mock
    private EmailOtpRepository repository;

    @Mock
    private OtpEmailSender sender;

    private EmailOtpService service;

    @BeforeEach
    void setUp() {
        service = new EmailOtpService(repository, sender, SECRET, EXPIRATION, COOLDOWN, 5,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- send ----

    @Test
    void sendEmailsSixDigitCodeAndStoresOnlyItsHash() {
        when(repository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.empty());

        SendOtpResponse response = service.send(EMAIL, Locale.ENGLISH);

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).sendRegistrationOtp(eq(EMAIL), code.capture(), eq(EXPIRATION), eq(Locale.ENGLISH));
        ArgumentCaptor<EmailOtp> saved = ArgumentCaptor.forClass(EmailOtp.class);
        verify(repository).saveAndFlush(saved.capture());

        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(saved.getValue().getOtpHash())
                .isEqualTo(service.hash(EMAIL, code.getValue()))
                .doesNotContain(code.getValue())
                .hasSize(64);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(EXPIRATION));
        assertThat(response).isEqualTo(new SendOtpResponse(EMAIL, 600, 60));
    }

    @Test
    void sendIsRateLimitedDuringCooldown() {
        EmailOtp existing = new EmailOtp(EMAIL, "hash", NOW.plus(EXPIRATION), NOW.minusSeconds(20));
        when(repository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.send(EMAIL, Locale.ENGLISH))
                .isInstanceOf(OtpRateLimitException.class)
                .hasMessage("Please wait 40 seconds before requesting another OTP");
        verifyNoInteractions(sender);
    }

    @Test
    void resendAfterCooldownReplacesOtpAndResetsAttempts() {
        EmailOtp existing = new EmailOtp(EMAIL, "old-hash", NOW.plusSeconds(1), NOW.minus(COOLDOWN));
        existing.recordFailedAttempt();
        when(repository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(existing));

        service.send(EMAIL, Locale.ENGLISH);

        assertThat(existing.getOtpHash()).isNotEqualTo("old-hash");
        assertThat(existing.getFailedAttempts()).isZero();
        assertThat(existing.getLastSentAt()).isEqualTo(NOW);
    }

    @Test
    void sendPropagatesEmailFailure() {
        when(repository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.empty());
        doThrow(new EmailDeliveryException("down", null))
                .when(sender).sendRegistrationOtp(anyString(), anyString(), any(), any());

        // The exception rolls back the transaction, so the stored OTP and cooldown are discarded
        assertThatThrownBy(() -> service.send(EMAIL, Locale.ENGLISH)).isInstanceOf(EmailDeliveryException.class);
    }

    // ---- verifyAndConsume ----

    @Test
    void correctOtpIsAcceptedAndUsedUp() {
        EmailOtp stored = stored("123456", NOW.plusSeconds(60));

        service.verifyAndConsume(EMAIL, "123456");

        verify(repository).delete(stored);
    }

    @Test
    void otpIsBoundToItsEmail() {
        EmailOtp stored = new EmailOtp(EMAIL, service.hash("someone.else@example.com", "123456"),
                NOW.plusSeconds(60), NOW);
        when(repository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.verifyAndConsume(EMAIL, "123456"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void wrongOtpCountsAttemptAndKeepsOtp() {
        EmailOtp stored = stored("123456", NOW.plusSeconds(60));

        assertThatThrownBy(() -> service.verifyAndConsume(EMAIL, "000000"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid OTP. 4 attempt(s) left");
        assertThat(stored.getFailedAttempts()).isEqualTo(1);
        verify(repository, never()).delete(any());
    }

    @Test
    void fifthWrongAttemptDeletesOtp() {
        EmailOtp stored = stored("123456", NOW.plusSeconds(60));
        for (int i = 0; i < 4; i++) {
            stored.recordFailedAttempt();
        }

        assertThatThrownBy(() -> service.verifyAndConsume(EMAIL, "000000"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Too many wrong attempts. Please request a new OTP");
        verify(repository).delete(stored);
    }

    @Test
    void expiredOtpIsRejectedEvenIfCorrect() {
        EmailOtp stored = stored("123456", NOW);

        assertThatThrownBy(() -> service.verifyAndConsume(EMAIL, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("OTP has expired. Please request a new one");
        verify(repository).delete(stored);
    }

    @Test
    void missingOtpIsRejected() {
        when(repository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndConsume(EMAIL, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("No active OTP for this email. Please request a new one");
    }

    private EmailOtp stored(String code, Instant expiresAt) {
        EmailOtp otp = new EmailOtp(EMAIL, service.hash(EMAIL, code), expiresAt, NOW.minusSeconds(30));
        when(repository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(otp));
        return otp;
    }
}
