package com.phenom.selfservicebooking.auth.dto;

/** Shared email rules. Stricter than plain @Email, which accepts addresses with no domain dot like "a@b". */
public final class EmailRules {

    public static final String REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    public static final String MESSAGE = "must be a valid email address";

    private EmailRules() {
    }
}
