package com.phenom.selfservicebooking.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record BookingConfirmationRequest(
        @NotNull
        @JsonProperty("candidate_id")
        Long candidateId,

        @NotEmpty
        @JsonProperty("host_ids")
        List<@NotNull Long> hostIds,

        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd")
        @JsonProperty("date")
        LocalDate date,

        @NotNull
        @JsonFormat(pattern = "HH:mm[:ss]")
        @JsonProperty("start_time")
        LocalTime startTime,

        @NotNull
        @JsonFormat(pattern = "HH:mm[:ss]")
        @JsonProperty("end_time")
        LocalTime endTime
) {

    @JsonIgnore
    @AssertTrue(message = "end_time must be after start_time")
    public boolean isEndAfterStart() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @JsonIgnore
    public LocalDateTime startDateTime() {
        return date.atTime(startTime);
    }

    @JsonIgnore
    public LocalDateTime endDateTime() {
        return date.atTime(endTime);
    }
}
