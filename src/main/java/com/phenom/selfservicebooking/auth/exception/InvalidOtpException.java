package com.phenom.selfservicebooking.auth.exception;

/** Wrong, expired, missing or locked-out email OTP. */
public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException(String message) {
        super(message);
    }
}
