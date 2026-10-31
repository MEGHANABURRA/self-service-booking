package com.selfservice.slotcache.api;

import com.selfservice.slotcache.api.Dtos.ErrorResponse;
import com.selfservice.slotcache.service.Exceptions.DriveLoadingException;
import com.selfservice.slotcache.service.Exceptions.DriveNotLoadedException;
import com.selfservice.slotcache.service.Exceptions.SlotUnavailableException;
import com.selfservice.slotcache.service.Exceptions.StaleSnapshotException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.DateTimeException;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Booking service should call POST /snapshots and compute. */
    @ExceptionHandler(DriveNotLoadedException.class)
    ResponseEntity<ErrorResponse> notLoaded(DriveNotLoadedException e) {
        return error(HttpStatus.NOT_FOUND, "DRIVE_NOT_LOADED", e.getMessage());
    }

    /** Someone else is computing: wait briefly and read again (do not ask to compute). */
    @ExceptionHandler(DriveLoadingException.class)
    ResponseEntity<ErrorResponse> loading(DriveLoadingException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "1")
                .body(new ErrorResponse("DRIVE_LOADING", e.getMessage()));
    }

    @ExceptionHandler(SlotUnavailableException.class)
    ResponseEntity<ErrorResponse> unavailable(SlotUnavailableException e) {
        return error(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(StaleSnapshotException.class)
    ResponseEntity<ErrorResponse> stale(StaleSnapshotException e) {
        return error(HttpStatus.CONFLICT, "STALE_SNAPSHOT", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class, ConstraintViolationException.class,
            MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
