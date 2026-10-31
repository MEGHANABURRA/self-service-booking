package com.phenom.selfservicebooking.auth.exception;

/** Valid access token, but the user's role isn't allowed to call this API. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
