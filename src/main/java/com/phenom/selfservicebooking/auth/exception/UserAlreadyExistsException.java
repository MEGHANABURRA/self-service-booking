package com.phenom.selfservicebooking.auth.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String userName) {
        super("Username '" + userName + "' is already taken");
    }
}
