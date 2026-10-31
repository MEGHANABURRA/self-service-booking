package com.phenom.selfservicebooking.host.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "host")
public class Host {

    @Id
    private Long id;

    @Column(name = "email_id", nullable = false)
    private String emailId;

    @Column(name = "name")
    private String name;

    // IANA zone, e.g. Asia/Kolkata; null falls back to the calendar time zone
    @Column(name = "time_zone")
    private String timeZone;

    protected Host() {
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
