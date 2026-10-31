package com.phenom.selfservicebooking.auth.exception;

import com.phenom.selfservicebooking.auth.entity.UserRole;

/** A user can be either a HOST or a CANDIDATE, never both. */
public class RoleConflictException extends RuntimeException {

    public RoleConflictException(String userName, UserRole existingRole) {
        super("'" + userName + "' is already registered as " + existingRole
                + ". A user can have only one role, HOST or CANDIDATE");
    }
}
