package com.phenom.selfservicebooking.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record BookingConfirmationResponse(
        @JsonProperty("booking_id")
        UUID bookingId,

        @JsonProperty("candidate_id")
        Long candidateId,

        @JsonProperty("host_ids")
        List<Long> hostIds,

        @JsonFormat(pattern = "yyyy-MM-dd")
        @JsonProperty("date")
        LocalDate date,

        @JsonFormat(pattern = "HH:mm:ss")
        @JsonProperty("start_time")
        LocalTime startTime,

        @JsonFormat(pattern = "HH:mm:ss")
        @JsonProperty("end_time")
        LocalTime endTime,

        @JsonProperty("meet_link")
        String meetLink
) {
}
