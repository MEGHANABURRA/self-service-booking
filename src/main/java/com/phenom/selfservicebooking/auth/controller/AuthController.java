package com.phenom.selfservicebooking.auth.controller;

import com.phenom.selfservicebooking.auth.dto.EmailRules;
import com.phenom.selfservicebooking.auth.dto.LoginRequest;
import com.phenom.selfservicebooking.auth.dto.LoginResponse;
import com.phenom.selfservicebooking.auth.dto.RefreshTokenRequest;
import com.phenom.selfservicebooking.auth.dto.RegisterRequest;
import com.phenom.selfservicebooking.auth.dto.SendOtpRequest;
import com.phenom.selfservicebooking.auth.dto.SendOtpResponse;
import com.phenom.selfservicebooking.auth.dto.UserResponse;
import com.phenom.selfservicebooking.auth.dto.UsernameAvailabilityResponse;
import com.phenom.selfservicebooking.auth.service.AuthService;
import com.phenom.selfservicebooking.common.constants.APIConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(APIConstants.AUTH_BASE)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Emails a 6-digit OTP to verify the address before registering. The email is in Hindi if the request's
     * Accept-Language asks for it, otherwise English.
     */
    @PostMapping(APIConstants.SEND_EMAIL_OTP)
    public SendOtpResponse sendEmailOtp(@Valid @RequestBody SendOtpRequest request, Locale locale) {
        return authService.sendRegistrationOtp(request, locale);
    }

    /** Registers a HOST or CANDIDATE (chosen by userRole). Requires the OTP emailed to userName. */
    @PostMapping(APIConstants.REGISTER)
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Tells whether a username is free to register. Deleted users' usernames count as free. */
    @GetMapping(APIConstants.USERNAME_AVAILABILITY)
    public UsernameAvailabilityResponse checkUsernameAvailability(
            @RequestParam @NotBlank @Email(regexp = EmailRules.REGEX, message = EmailRules.MESSAGE) String userName) {
        return authService.checkUsernameAvailability(userName);
    }

    @PostMapping(APIConstants.LOGIN)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Swaps a refresh token for a new access token + refresh token. Doesn't need an access token. */
    @PostMapping(APIConstants.REFRESH_TOKEN)
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }
}
