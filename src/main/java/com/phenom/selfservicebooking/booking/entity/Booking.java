package com.phenom.selfservicebooking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "booking")
@SQLRestriction("is_deleted = false")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "host_ids", nullable = false, columnDefinition = "bigint[]")
    private List<Long> hostIds;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Column(name = "meet_link", nullable = false)
    private String meetLink;

    @Column(name = "calendar_event_id", nullable = false)
    private String calendarEventId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean not null default false")
    private boolean deleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Booking() {
    }

    public Booking(Long candidateId, List<Long> hostIds, LocalDateTime startDateTime, LocalDateTime endDateTime,
                   String meetLink, String calendarEventId) {
        this.candidateId = candidateId;
        this.hostIds = hostIds;
        this.bookingDate = startDateTime.toLocalDate();
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.meetLink = meetLink;
        this.calendarEventId = calendarEventId;
    }

    public UUID getId() {
        return id;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public List<Long> getHostIds() {
        return hostIds;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public String getMeetLink() {
        return meetLink;
    }

    public String getCalendarEventId() {
        return calendarEventId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
