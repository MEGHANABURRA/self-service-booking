package com.phenom.selfservicebooking.common.email;

/** One HTML email to one recipient. */
public record EmailMessage(String from, String to, String subject, String html) {
}
