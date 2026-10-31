package com.phenom.selfservicebooking.auth.exception;

/** Missing, malformed, expired or otherwise invalid access token. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
