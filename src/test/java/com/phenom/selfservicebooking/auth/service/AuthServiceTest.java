package com.phenom.selfservicebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phenom.selfservicebooking.auth.dto.LoginRequest;
import com.phenom.selfservicebooking.auth.dto.LoginResponse;
import com.phenom.selfservicebooking.auth.dto.RefreshTokenRequest;
import com.phenom.selfservicebooking.auth.dto.RegisterRequest;
import com.phenom.selfservicebooking.auth.dto.SendOtpRequest;
import com.phenom.selfservicebooking.auth.dto.SendOtpResponse;
import com.phenom.selfservicebooking.auth.dto.UserResponse;
import com.phenom.selfservicebooking.auth.entity.User;
import com.phenom.selfservicebooking.auth.entity.UserRole;
import com.phenom.selfservicebooking.auth.exception.InvalidCredentialsException;
import com.phenom.selfservicebooking.auth.exception.InvalidOtpException;
import com.phenom.selfservicebooking.auth.exception.InvalidTimeZoneException;
import com.phenom.selfservicebooking.auth.exception.RoleConflictException;
import com.phenom.selfservicebooking.auth.exception.UserAlreadyExistsException;
import com.phenom.selfservicebooking.auth.repository.UserRepository;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.phenom.selfservicebooking.auth.config.PasswordEncoderConfig;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "SQ1j3tC3Bun+WwpgQsgVo2X4hjdeLMj9KbyGlA6bX8I=";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailOtpService emailOtpService;

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();
    private final JwtService jwtService = new JwtService(SECRET, Duration.ofMinutes(30));
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService, emailOtpService);
    }

    @Test
    void registerStoresHashedPasswordAndNormalizedTimeZone() {
        when(userRepository.findByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "userId", 42L);
            return saved;
        });

        UserResponse response = authService.register(
                new RegisterRequest("John Doe", " John.Doe@Example.com ", "S3cret!Pass", "Asia/Kolkata", UserRole.HOST, "123456"));

        verify(emailOtpService).verifyAndConsume("john.doe@example.com", "123456");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        // base64(16-byte salt + 32-byte PBKDF2-HMAC-SHA512 hash)
        assertThat(saved.getPassword()).hasSize(64).matches("[A-Za-z0-9+/]+");
        assertThat(passwordEncoder.matches("S3cret!Pass", saved.getPassword())).isTrue();
        assertThat(saved.getUserName()).isEqualTo("john.doe@example.com");
        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.userRole()).isEqualTo(UserRole.HOST);
    }

    @Test
    void registerRejectsDuplicateUserNameWithSameRole() {
        User existing = existingUser(7L, "S3cret!Pass"); // a CANDIDATE
        when(userRepository.findByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("John", "john.doe@example.com", "S3cret!Pass", "UTC", UserRole.CANDIDATE, "123456")))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(emailOtpService);
    }

    @Test
    void registerRejectsSameUserWithDifferentRole() {
        User existing = existingUser(7L, "S3cret!Pass"); // a CANDIDATE
        when(userRepository.findByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("John", "John.Doe@example.com", "S3cret!Pass", "UTC", UserRole.HOST, "123456")))
                .isInstanceOf(RoleConflictException.class)
                .hasMessage("'john.doe@example.com' is already registered as CANDIDATE. "
                        + "A user can have only one role, HOST or CANDIDATE");
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(emailOtpService);
    }

    @Test
    void registerRejectsInvalidTimeZone() {
        when(userRepository.findByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("John", "john.doe@example.com", "S3cret!Pass", "Mars/Olympus", UserRole.CANDIDATE, "123456")))
                .isInstanceOf(InvalidTimeZoneException.class);
        verifyNoInteractions(emailOtpService);   // a typo elsewhere doesn't use up the OTP
    }

    @Test
    void registerFailsAndSavesNothingWhenOtpIsWrong() {
        when(userRepository.findByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(Optional.empty());
        doThrow(new InvalidOtpException("Invalid OTP. 4 attempt(s) left"))
                .when(emailOtpService).verifyAndConsume("john.doe@example.com", "999999");

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("John", "john.doe@example.com", "S3cret!Pass", "UTC", UserRole.HOST, "999999")))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid OTP. 4 attempt(s) left");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendRegistrationOtpSendsToNormalizedEmail() {
        when(userRepository.existsByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(false);
        var expected = new SendOtpResponse("john.doe@example.com", 600, 60);
        when(emailOtpService.send("john.doe@example.com", Locale.ENGLISH)).thenReturn(expected);

        assertThat(authService.sendRegistrationOtp(new SendOtpRequest(" John.Doe@Example.com "), Locale.ENGLISH))
                .isEqualTo(expected);
    }

    @Test
    void sendRegistrationOtpRefusesRegisteredEmail() {
        when(userRepository.existsByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.sendRegistrationOtp(
                new SendOtpRequest("john.doe@example.com"), Locale.ENGLISH))
                .isInstanceOf(UserAlreadyExistsException.class);
        verifyNoInteractions(emailOtpService);
    }

    @Test
    void usernameIsAvailableWhenNoActiveUserHasIt() {
        when(userRepository.existsByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(false);

        var response = authService.checkUsernameAvailability(" John.Doe@Example.com ");

        assertThat(response.userName()).isEqualTo("john.doe@example.com");
        assertThat(response.available()).isTrue();
    }

    @Test
    void usernameIsUnavailableWhenTaken() {
        when(userRepository.existsByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(true);

        assertThat(authService.checkUsernameAvailability("john.doe@example.com").available()).isFalse();
    }

    @Test
    void loginReturnsJwtWithUserIdAsSubject() {
        User user = existingUser(7L, "S3cret!Pass");
        when(userRepository.findByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenService.issue(user)).thenReturn("refresh-1");
        when(refreshTokenService.getExpirationSeconds()).thenReturn(604800L);

        LoginResponse response = authService.login(new LoginRequest("john.doe@example.com", "S3cret!Pass"));

        var claims = jwtService.parseToken(response.accessToken());
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("role", String.class)).isEqualTo("CANDIDATE");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(1800);
        assertThat(response.refreshToken()).isEqualTo("refresh-1");
        assertThat(response.refreshExpiresInSeconds()).isEqualTo(604800);
    }

    @Test
    void refreshReturnsNewAccessAndRefreshTokens() {
        User user = existingUser(7L, "S3cret!Pass");
        when(refreshTokenService.rotate("refresh-1"))
                .thenReturn(new RefreshTokenService.Rotation(user, "refresh-2"));
        when(refreshTokenService.getExpirationSeconds()).thenReturn(604800L);

        LoginResponse response = authService.refresh(new RefreshTokenRequest(" refresh-1 "));

        assertThat(jwtService.parseToken(response.accessToken()).getSubject()).isEqualTo("7");
        assertThat(response.refreshToken()).isEqualTo("refresh-2");
        assertThat(response.user().userId()).isEqualTo(7L);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = existingUser(7L, "S3cret!Pass");
        when(userRepository.findByUserNameAndDeletedFalse("john.doe@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("john.doe@example.com", "Wr0ng!Pass")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userRepository.findByUserNameAndDeletedFalse("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "Whatever1!")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private User existingUser(Long id, String rawPassword) {
        User user = new User();
        ReflectionTestUtils.setField(user, "userId", id);
        user.setName("John Doe");
        user.setUserName("john.doe@example.com");
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setTimeZone("UTC");
        user.setUserRole(UserRole.CANDIDATE);
        return user;
    }
}
