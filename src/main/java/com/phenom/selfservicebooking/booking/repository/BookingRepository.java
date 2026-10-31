package com.phenom.selfservicebooking.booking.repository;

import com.phenom.selfservicebooking.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query(value = "SELECT COUNT(*) FROM booking WHERE booking_date = :bookingDate AND :hostId = ANY(host_ids)"
            + " AND is_deleted = false",
            nativeQuery = true)
    long countByHostIdAndBookingDate(@Param("hostId") Long hostId, @Param("bookingDate") LocalDate bookingDate);
}
