package com.phenom.selfservicebooking.auth.dto;

import com.phenom.selfservicebooking.auth.entity.UserRole;
import com.phenom.selfservicebooking.auth.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 254) @Email(regexp = EmailRules.REGEX, message = EmailRules.MESSAGE) String userName,
        @NotBlank @StrongPassword String password,
        @NotBlank @Size(max = 50) String timeZone,
        @NotNull UserRole userRole) {
}
