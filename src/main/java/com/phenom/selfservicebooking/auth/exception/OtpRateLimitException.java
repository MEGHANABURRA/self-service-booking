package com.phenom.selfservicebooking.auth.exception;

/** An OTP was sent to this email too recently. */
public class OtpRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public OtpRateLimitException(long retryAfterSeconds) {
        super("Please wait " + retryAfterSeconds + " seconds before requesting another OTP");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
