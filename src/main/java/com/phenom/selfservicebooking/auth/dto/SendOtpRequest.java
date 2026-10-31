package com.phenom.selfservicebooking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendOtpRequest(
        @NotBlank @Size(max = 254) @Email(regexp = EmailRules.REGEX, message = EmailRules.MESSAGE) String email) {
}
