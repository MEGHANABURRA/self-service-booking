package com.phenom.selfservicebooking.auth.dto;

/** The OTP was emailed. It's valid for expiresInSeconds; a new one can be requested after resendAfterSeconds. */
public record SendOtpResponse(String email, long expiresInSeconds, long resendAfterSeconds) {
}
