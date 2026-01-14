package com.example.yugioh.exception;

import com.example.yugioh.dto.ResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Centralized exception handling for REST controller endpoints.
 * Only catches exceptions from synchronous controller methods.
 * Does NOT catch: @Async, @Scheduled, CommandLineRunner (must use try-catch).
 * 
 * Why same constraint violation produces different errors:
 * - "Data integrity violation" (handleDataIntegrity): Direct database constraint hit, no transaction side effects
 * - "Transaction rolled back" (handleGeneral): Constraint hit after cache/background operations started
 * 
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @Valid validation errors on request body objects.
     * Triggered when @NotBlank, @Size, @Pattern, etc. fail on DTOs/entities.
     * Returns first validation error message in consistent ResponseEnvelope format.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseEnvelope<Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .findFirst()
            .orElse("Validation failed");
        
        return ResponseEntity.status(400)
            .body(ResponseEnvelope.failed(errorMessage));
    }

    // Handles direct database constraint violations (foreign key, unique constraint, etc.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseEnvelope<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = "Data integrity violation";
        if (ex.getMessage() != null && ex.getMessage().contains("duplicate key")) {
            message = "Resource already exists";
        }
        return ResponseEntity.status(409)
            .body(ResponseEnvelope.failed(message));
    }

    // Catches all other exceptions including transaction rollback errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseEnvelope<Object>> handleGeneral(Exception ex) {
        return ResponseEntity.status(500)
            .body(ResponseEnvelope.failed("Internal server error: " + ex.getMessage()));
    }
}
