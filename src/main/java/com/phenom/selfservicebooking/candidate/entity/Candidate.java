package com.phenom.selfservicebooking.candidate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    private Long id;

    @Column(name = "email_id", nullable = false)
    private String emailId;

    @Column(name = "name")
    private String name;

    // IANA zone, e.g. Asia/Kolkata; null falls back to the calendar time zone
    @Column(name = "time_zone")
    private String timeZone;

    protected Candidate() {
    }

    public Long getId() {
        return id;
    }

    public String getEmailId() {
        return emailId;
    }

    public String getName() {
        return name;
    }

    public String getTimeZone() {
        return timeZone;
    }
}
