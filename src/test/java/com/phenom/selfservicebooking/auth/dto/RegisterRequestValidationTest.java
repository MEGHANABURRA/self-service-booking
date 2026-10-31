package com.phenom.selfservicebooking.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.phenom.selfservicebooking.auth.entity.UserRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegisterRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Map<String, String> errors(String userName, String password) {
        return errors(userName, password, "123456");
    }

    private static Map<String, String> errors(String userName, String password, String otp) {
        var request = new RegisterRequest("John Doe", userName, password, "UTC", UserRole.CANDIDATE, otp);
        return VALIDATOR.validate(request).stream().collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage, (a, b) -> a + " | " + b));
    }

    @Test
    void acceptsValidEmailAndStrongPassword() {
        assertThat(errors("john.doe@example.com", "S3cret!Pass")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdoe", "jdoe@", "@example.com", "jdoe@example", "j doe@example.com", "a@b@c.com"})
    void rejectsInvalidEmails(String userName) {
        assertThat(errors(userName, "S3cret!Pass")).containsEntry("userName", "must be a valid email address");
    }

    @Test
    void reportsEveryMissingPasswordRule() {
        assertThat(errors("john.doe@example.com", "abc")).containsEntry("password",
                "must be 8-128 characters long, contain an uppercase letter, contain a digit, "
                        + "contain a special character (e.g. !@#$%^&*)");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "s3cret!pass",   // no uppercase
            "S3CRET!PASS",   // no lowercase
            "Secret!Pass",   // no digit
            "S3cretPass1",   // no special character
            "S3cret! Pass",  // contains a space
            "S3!p"           // too short
    })
    void rejectsWeakPasswords(String password) {
        assertThat(errors("john.doe@example.com", password)).containsKey("password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "12345a", "12 456"})
    void rejectsOtpThatIsNotSixDigits(String otp) {
        assertThat(errors("john.doe@example.com", "S3cret!Pass", otp))
                .containsEntry("otp", "must be the 6-digit code sent to your email");
    }

    @Test
    void requiresOtp() {
        assertThat(errors("john.doe@example.com", "S3cret!Pass", null)).containsKey("otp");
    }
}
