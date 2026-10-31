package com.phenom.selfservicebooking.common.email;

/** Sends emails. The app's only implementation is {@link JavaMailEmailSender} (SMTP). */
public interface EmailSender {

    /** Sends the email, or throws {@link EmailDeliveryException} if the provider didn't accept it. */
    void send(EmailMessage message);
}
