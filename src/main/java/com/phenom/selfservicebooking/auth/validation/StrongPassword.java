package com.phenom.selfservicebooking.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Password must be 8-128 characters with at least one uppercase letter, one lowercase letter,
 * one digit and one special character, and no whitespace. Null is left to @NotBlank.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "is not strong enough";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
