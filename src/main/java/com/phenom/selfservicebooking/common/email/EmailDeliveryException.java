package com.phenom.selfservicebooking.common.email;

/**
 * The SMTP server didn't accept the email (unreachable, wrong credentials, rejected recipient, ...).
 * The message is for logs; API clients get a generic message.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
