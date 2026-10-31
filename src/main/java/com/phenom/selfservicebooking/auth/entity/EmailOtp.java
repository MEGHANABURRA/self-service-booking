package com.phenom.selfservicebooking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * The current OTP sent to an email address for registration. One row per email: sending a new OTP replaces
 * the old one, and the row is deleted once the OTP is used, expires, or has too many wrong attempts.
 * Only an HMAC of the OTP is stored, never the code itself.
 */
@Entity
@Table(name = "email_otps")
public class EmailOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_otp_id")
    private Long emailOtpId;

    @Column(name = "email", nullable = false, updatable = false, unique = true)
    private String email;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailOtp() {
        // for JPA
    }

    public EmailOtp(String email, String otpHash, Instant expiresAt, Instant sentAt) {
        this.email = email;
        reissue(otpHash, expiresAt, sentAt);
    }

    /** Replaces the OTP with a newly sent one and resets the wrong-attempt count. */
    public void reissue(String otpHash, Instant expiresAt, Instant sentAt) {
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.lastSentAt = sentAt;
        this.failedAttempts = 0;
    }

    /** Returns the new number of wrong attempts. */
    public int recordFailedAttempt() {
        return ++failedAttempts;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public Long getEmailOtpId() {
        return emailOtpId;
    }

    public String getEmail() {
        return email;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
