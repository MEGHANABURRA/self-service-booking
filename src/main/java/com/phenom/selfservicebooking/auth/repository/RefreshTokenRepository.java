package com.phenom.selfservicebooking.auth.repository;

import com.phenom.selfservicebooking.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Locks the row so two simultaneous refreshes with the same token can't both succeed.
     * Fetches the user too, since it's needed after the transaction to build the new access token.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Ends every session of the user. */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.user.userId = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);
}
