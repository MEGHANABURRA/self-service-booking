package com.phenom.selfservicebooking.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }

        List<String> problems = new ArrayList<>();
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            problems.add("be " + MIN_LENGTH + "-" + MAX_LENGTH + " characters long");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            problems.add("contain an uppercase letter");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            problems.add("contain a lowercase letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("contain a digit");
        }
        if (password.chars().noneMatch(StrongPasswordValidator::isSpecial)) {
            problems.add("contain a special character (e.g. !@#$%^&*)");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            problems.add("not contain spaces");
        }

        if (problems.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("must " + String.join(", ", problems))
                .addConstraintViolation();
        return false;
    }

    private static boolean isSpecial(int c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }
}
