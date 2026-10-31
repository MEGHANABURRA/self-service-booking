package com.phenom.selfservicebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phenom.selfservicebooking.auth.entity.RefreshToken;
import com.phenom.selfservicebooking.auth.entity.User;
import com.phenom.selfservicebooking.auth.entity.UserRole;
import com.phenom.selfservicebooking.auth.exception.UnauthorizedException;
import com.phenom.selfservicebooking.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, TTL, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        ReflectionTestUtils.setField(user, "userId", 7L);
        user.setUserRole(UserRole.HOST);
    }

    @Test
    void issueStoresOnlyTheHashWithExpiry() {
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+");    // 32 random bytes, base64url
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw).hasSize(64);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(saved.getUser()).isSameAs(user);
    }

    @Test
    void issueGeneratesDifferentTokensEachTime() {
        assertThat(service.issue(user)).isNotEqualTo(service.issue(user));
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOne() {
        RefreshToken current = stored("old-token", NOW.plus(Duration.ofDays(1)));

        RefreshTokenService.Rotation rotation = service.rotate("old-token");

        assertThat(current.isRevoked()).isTrue();
        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.refreshToken()).isNotEqualTo("old-token");
        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("nope"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void rotateRejectsExpiredToken() {
        stored("old-token", NOW);   // expires exactly now

        assertThatThrownBy(() -> service.rotate("old-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token has expired. Please log in again");
        verify(repository, never()).save(any());
    }

    @Test
    void reusingRevokedTokenRevokesAllSessionsOfUser() {
        RefreshToken current = stored("old-token", NOW.plus(Duration.ofDays(1)));
        current.revoke();

        assertThatThrownBy(() -> service.rotate("old-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token has already been used. Please log in again");
        verify(repository, times(1)).revokeAllForUser(7L);
        verify(repository, never()).save(any());
    }

    @Test
    void rotateRejectsTokenOfDeletedUser() {
        RefreshToken current = stored("old-token", NOW.plus(Duration.ofDays(1)));
        user.setDeleted(true);

        assertThatThrownBy(() -> service.rotate("old-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
        assertThat(current.isRevoked()).isTrue();
        verify(repository, never()).save(any());
    }

    private RefreshToken stored(String raw, Instant expiresAt) {
        RefreshToken token = new RefreshToken(user, RefreshTokenService.hash(raw), expiresAt);
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash(raw))).thenReturn(Optional.of(token));
        return token;
    }
}
