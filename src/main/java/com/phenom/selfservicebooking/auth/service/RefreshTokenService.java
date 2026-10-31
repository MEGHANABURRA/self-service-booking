package com.phenom.selfservicebooking.auth.service;

import com.phenom.selfservicebooking.auth.entity.RefreshToken;
import com.phenom.selfservicebooking.auth.entity.User;
import com.phenom.selfservicebooking.auth.exception.UnauthorizedException;
import com.phenom.selfservicebooking.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and rotates refresh tokens.
 *
 * <p>A refresh token is 256 random bits (not a JWT, so it can never be used as an access token). The client
 * gets the raw value once; the database only keeps its SHA-256 hash. Every refresh revokes the presented
 * token and issues a new one. If a revoked token is presented again, it was most likely stolen and replayed,
 * so all of that user's refresh tokens are revoked and they must log in again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration expiration;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${security.refresh-token.expiration}") Duration expiration) {
        this(refreshTokenRepository, expiration, Clock.systemUTC());
    }

    RefreshTokenService(RefreshTokenRepository refreshTokenRepository, Duration expiration, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expiration = expiration;
        this.clock = clock;
    }

    /** Creates a new refresh token for the user and returns its raw value (shown to the client only once). */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(new RefreshToken(user, hash(rawToken), clock.instant().plus(expiration)));
        return rawToken;
    }

    /**
     * Uses up the given refresh token and issues a replacement.
     *
     * <p>Doesn't roll back on UnauthorizedException, so revoking all sessions on token reuse is kept.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        User user = current.getUser();

        if (current.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllForUser(user.getUserId());
            log.warn("Reuse of revoked refresh token {} for user {}; revoked {} active session(s)",
                    current.getRefreshTokenId(), user.getUserId(), revoked);
            throw new UnauthorizedException("Refresh token has already been used. Please log in again");
        }
        if (current.isExpired(clock.instant())) {
            throw new UnauthorizedException("Refresh token has expired. Please log in again");
        }
        if (user.isDeleted()) {
            current.revoke();
            throw new UnauthorizedException("Invalid refresh token");
        }

        current.revoke();
        return new Rotation(user, issue(user));
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }

    /** SHA-256 is enough here: the token is 256 random bits, so it can't be brute-forced like a password. */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** The user the token belonged to, and their new refresh token. */
    public record Rotation(User user, String refreshToken) {
    }
}
