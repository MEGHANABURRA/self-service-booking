package com.phenom.selfservicebooking.auth.service;

import com.phenom.selfservicebooking.auth.dto.SendOtpResponse;
import com.phenom.selfservicebooking.auth.entity.EmailOtp;
import com.phenom.selfservicebooking.auth.exception.InvalidOtpException;
import com.phenom.selfservicebooking.auth.exception.OtpRateLimitException;
import com.phenom.selfservicebooking.auth.repository.EmailOtpRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends and checks the 6-digit OTP that proves a user owns the email address they register with.
 *
 * <p>Only HMAC-SHA256(secret, email + otp) is stored. A plain hash of a 6-digit code could be reversed by
 * trying all million codes; the server-side secret prevents that if the database leaks.
 */
@Service
public class EmailOtpService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int OTP_BOUND = 1_000_000;   // 6 digits

    private final EmailOtpRepository emailOtpRepository;
    private final OtpEmailSender otpEmailSender;
    private final SecretKeySpec hmacKey;
    private final Duration expiration;
    private final Duration resendCooldown;
    private final int maxAttempts;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public EmailOtpService(
            EmailOtpRepository emailOtpRepository,
            OtpEmailSender otpEmailSender,
            @Value("${security.otp.secret}") String base64Secret,
            @Value("${security.otp.expiration}") Duration expiration,
            @Value("${security.otp.resend-cooldown}") Duration resendCooldown,
            @Value("${security.otp.max-attempts}") int maxAttempts) {
        this(emailOtpRepository, otpEmailSender, base64Secret, expiration, resendCooldown, maxAttempts,
                Clock.systemUTC());
    }

    EmailOtpService(EmailOtpRepository emailOtpRepository, OtpEmailSender otpEmailSender, String base64Secret,
                    Duration expiration, Duration resendCooldown, int maxAttempts, Clock clock) {
        this.emailOtpRepository = emailOtpRepository;
        this.otpEmailSender = otpEmailSender;
        this.hmacKey = new SecretKeySpec(Base64.getDecoder().decode(base64Secret), HMAC_ALGORITHM);
        this.expiration = expiration;
        this.resendCooldown = resendCooldown;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
    }

    /**
     * Generates a new OTP for the (already normalized) email and emails it, replacing any earlier OTP.
     * If the email can't be sent, the transaction rolls back, so nothing is saved and the user can retry
     * right away.
     */
    @Transactional
    public SendOtpResponse send(String email, Locale locale) {
        Instant now = clock.instant();
        EmailOtp existing = emailOtpRepository.findByEmailForUpdate(email).orElse(null);
        if (existing != null) {
            Instant nextAllowed = existing.getLastSentAt().plus(resendCooldown);
            if (now.isBefore(nextAllowed)) {
                throw new OtpRateLimitException(secondsUntil(now, nextAllowed));
            }
        }

        String otp = String.format("%06d", secureRandom.nextInt(OTP_BOUND));
        String otpHash = hash(email, otp);
        Instant expiresAt = now.plus(expiration);

        EmailOtp emailOtp = existing;
        if (emailOtp == null) {
            emailOtp = new EmailOtp(email, otpHash, expiresAt, now);
        } else {
            emailOtp.reissue(otpHash, expiresAt, now);
        }
        try {
            emailOtpRepository.saveAndFlush(emailOtp);
        } catch (DataIntegrityViolationException e) {
            // Another request inserted the first OTP for this email at the same moment
            throw new OtpRateLimitException(resendCooldown.toSeconds());
        }

        otpEmailSender.sendRegistrationOtp(email, otp, expiration, locale);
        return new SendOtpResponse(email, expiration.toSeconds(), resendCooldown.toSeconds());
    }

    /**
     * Checks the OTP for the (already normalized) email and uses it up if it's correct. Throws
     * InvalidOtpException otherwise. Doesn't roll back on that exception, so wrong attempts are still counted.
     */
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public void verifyAndConsume(String email, String otp) {
        EmailOtp emailOtp = emailOtpRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new InvalidOtpException(
                        "No active OTP for this email. Please request a new one"));

        if (emailOtp.isExpired(clock.instant())) {
            emailOtpRepository.delete(emailOtp);
            throw new InvalidOtpException("OTP has expired. Please request a new one");
        }

        if (!constantTimeEquals(hash(email, otp), emailOtp.getOtpHash())) {
            int attemptsLeft = maxAttempts - emailOtp.recordFailedAttempt();
            if (attemptsLeft <= 0) {
                emailOtpRepository.delete(emailOtp);
                throw new InvalidOtpException("Too many wrong attempts. Please request a new OTP");
            }
            throw new InvalidOtpException("Invalid OTP. " + attemptsLeft + " attempt(s) left");
        }

        emailOtpRepository.delete(emailOtp);
    }

    String hash(String email, String otp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static long secondsUntil(Instant now, Instant later) {
        long millis = Duration.between(now, later).toMillis();
        return Math.max(1, (millis + 999) / 1000);   // round up, never "wait 0 seconds"
    }
}
