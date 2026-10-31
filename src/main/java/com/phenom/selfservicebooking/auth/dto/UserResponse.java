package com.phenom.selfservicebooking.auth.dto;

import com.phenom.selfservicebooking.auth.entity.User;
import com.phenom.selfservicebooking.auth.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

/** User details returned by the API. Never includes the password hash. */
public record UserResponse(
        Long userId,
        UUID userUuid,
        String name,
        String userName,
        String timeZone,
        UserRole userRole,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getUserUuid(),
                user.getName(),
                user.getUserName(),
                user.getTimeZone(),
                user.getUserRole(),
                user.getCreatedAt());
    }
}
