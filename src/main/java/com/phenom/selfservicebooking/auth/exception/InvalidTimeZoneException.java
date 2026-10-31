package com.phenom.selfservicebooking.auth.exception;

public class InvalidTimeZoneException extends RuntimeException {

    public InvalidTimeZoneException(String timeZone) {
        super("Invalid time zone '" + timeZone + "'. Use an IANA zone id such as 'Asia/Kolkata'");
    }
}
