package com.phenom.selfservicebooking.auth.service;

import com.phenom.selfservicebooking.auth.dto.LoginRequest;
import com.phenom.selfservicebooking.auth.dto.LoginResponse;
import com.phenom.selfservicebooking.auth.dto.RegisterRequest;
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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String userName = normalizeUserName(request.userName());
        userRepository.findByUserNameAndDeletedFalse(userName).ifPresent(existing -> {
            // One user, one role: the same email can't be both a HOST and a CANDIDATE
            if (existing.getUserRole() != request.userRole()) {
                throw new RoleConflictException(userName, existing.getUserRole());
            }
            throw new UserAlreadyExistsException(userName);
        });

        User user = new User();
        user.setName(request.name().trim());
        user.setUserName(userName);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setTimeZone(validateTimeZone(request.timeZone()));
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

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUserNameAndDeletedFalse(normalizeUserName(request.userName()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpirationSeconds(),
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
