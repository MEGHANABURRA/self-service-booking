package com.phenom.selfservicebooking.auth.controller;

import com.phenom.selfservicebooking.auth.dto.EmailRules;
import com.phenom.selfservicebooking.auth.dto.LoginRequest;
import com.phenom.selfservicebooking.auth.dto.LoginResponse;
import com.phenom.selfservicebooking.auth.dto.RegisterRequest;
import com.phenom.selfservicebooking.auth.dto.UserResponse;
import com.phenom.selfservicebooking.auth.dto.UsernameAvailabilityResponse;
import com.phenom.selfservicebooking.auth.service.AuthService;
import com.phenom.selfservicebooking.common.constants.APIConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    /** Registers a HOST or CANDIDATE, chosen by the userRole field. */
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
}
