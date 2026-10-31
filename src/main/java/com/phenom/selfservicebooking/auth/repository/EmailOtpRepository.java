package com.phenom.selfservicebooking.auth.repository;

import com.phenom.selfservicebooking.auth.entity.EmailOtp;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    /**
     * Locks the row, so parallel requests can't get around the wrong-attempt limit or the resend cooldown.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from EmailOtp o where o.email = :email")
    Optional<EmailOtp> findByEmailForUpdate(@Param("email") String email);
}
