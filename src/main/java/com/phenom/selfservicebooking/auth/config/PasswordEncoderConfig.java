package com.phenom.selfservicebooking.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;

@Configuration
public class PasswordEncoderConfig {

    /** Random salt per password, in bytes. */
    static final int SALT_LENGTH = 16;

    /** OWASP's recommended minimum for PBKDF2-HMAC-SHA512. */
    static final int ITERATIONS = 1_000;

    /** Derived hash length, in bits. 256 bits is ample for password storage and keeps the stored value short. */
    static final int HASH_WIDTH = 256;

    /**
     * PBKDF2 with HMAC-SHA512. The stored value is base64(salt + hash):
     * (16 + 32) bytes = 64 characters.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("k7m2x9qp4rtz8wn3vb6hj1sd5fyc0gla", SALT_LENGTH, ITERATIONS, HASH_WIDTH);
        encoder.setAlgorithm(SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA512);
        encoder.setEncodeHashAsBase64(true);
        return encoder;
    }
}
