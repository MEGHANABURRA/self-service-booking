package com.phenom.selfservicebooking.booking.controller;

import com.phenom.selfservicebooking.booking.dto.BookingConfirmationRequest;
import com.phenom.selfservicebooking.booking.dto.BookingConfirmationResponse;
import com.phenom.selfservicebooking.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/confirmation")
    public ResponseEntity<BookingConfirmationResponse> createBookingConfirmation(
                    @Valid @RequestBody BookingConfirmationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBookingConfirmation(request));
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> cancelBooking(@PathVariable("bookingId") UUID bookingId) {
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.noContent().build();
    }
}
