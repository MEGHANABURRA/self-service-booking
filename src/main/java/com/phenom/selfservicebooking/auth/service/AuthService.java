package com.phenom.selfservicebooking.auth.service;

import com.phenom.selfservicebooking.auth.dto.LoginRequest;
import com.phenom.selfservicebooking.auth.dto.LoginResponse;
import com.phenom.selfservicebooking.auth.dto.RefreshTokenRequest;
import com.phenom.selfservicebooking.auth.dto.RegisterRequest;
import com.phenom.selfservicebooking.auth.dto.SendOtpRequest;
import com.phenom.selfservicebooking.auth.dto.SendOtpResponse;
import com.phenom.selfservicebooking.auth.dto.UserResponse;
import com.phenom.selfservicebooking.auth.dto.UsernameAvailabilityResponse;
import com.phenom.selfservicebooking.auth.entity.User;
import com.phenom.selfservicebooking.auth.exception.InvalidCredentialsException;
import com.phenom.selfservicebooking.auth.exception.InvalidTimeZoneException;
import com.phenom.selfservicebooking.auth.exception.RoleConflictException;
import com.phenom.selfservicebooking.auth.exception.UserAlreadyExistsException;
import com.phenom.selfservicebooking.auth.repository.UserRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmailOtpService emailOtpService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService, EmailOtpService emailOtpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.emailOtpService = emailOtpService;
    }

    /** Step 1 of registration: email an OTP to prove the user owns the address. */
    public SendOtpResponse sendRegistrationOtp(SendOtpRequest request, Locale locale) {
        String email = normalizeUserName(request.email());
        // Don't send codes to addresses that are already registered
        if (userRepository.existsByUserNameAndDeletedFalse(email)) {
            throw new UserAlreadyExistsException(email);
        }
        return emailOtpService.send(email, locale);
    }

    /**
     * Step 2 of registration: create the user, only if the OTP emailed to userName is correct.
     *
     * <p>Not @Transactional on purpose: verifyAndConsume() commits on its own, so a wrong OTP is still
     * counted as a failed attempt even though registration fails. Every other check runs before the OTP is
     * checked, so a typo in, say, the time zone doesn't use up a valid OTP.
     */
    public UserResponse register(RegisterRequest request) {
        String userName = normalizeUserName(request.userName());
        userRepository.findByUserNameAndDeletedFalse(userName).ifPresent(existing -> {
            // One user, one role: the same email can't be both a HOST and a CANDIDATE
            if (existing.getUserRole() != request.userRole()) {
                throw new RoleConflictException(userName, existing.getUserRole());
            }
            throw new UserAlreadyExistsException(userName);
        });
        String timeZone = validateTimeZone(request.timeZone());

        emailOtpService.verifyAndConsume(userName, request.otp().trim());

        User user = new User();
        user.setName(request.name().trim());
        user.setUserName(userName);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setTimeZone(timeZone);
        user.setUserRole(request.userRole());

        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException(userName);
        }
    }

    @Transactional(readOnly = true)
    public UsernameAvailabilityResponse checkUsernameAvailability(String userName) {
        String normalized = normalizeUserName(userName);
        return new UsernameAvailabilityResponse(normalized, !userRepository.existsByUserNameAndDeletedFalse(normalized));
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUserNameAndDeletedFalse(normalizeUserName(request.userName()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return tokensFor(user, refreshTokenService.issue(user));
    }

    /**
     * Exchanges a refresh token for a new access token and a new refresh token. The old refresh token
     * stops working. Not @Transactional itself: rotate() runs in its own transaction so that revoking
     * sessions on token reuse is committed even though the request fails.
     */
    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken().trim());
        return tokensFor(rotation.user(), rotation.refreshToken());
    }

    private LoginResponse tokensFor(User user, String refreshToken) {
        return new LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpirationSeconds(),
                refreshToken,
                refreshTokenService.getExpirationSeconds(),
                UserResponse.from(user));
    }

    private static String normalizeUserName(String userName) {
        return userName.trim().toLowerCase(Locale.ROOT);
    }

    private static String validateTimeZone(String timeZone) {
        try {
            return ZoneId.of(timeZone.trim()).getId();
        } catch (DateTimeException e) {
            throw new InvalidTimeZoneException(timeZone);
        }
    }
}
