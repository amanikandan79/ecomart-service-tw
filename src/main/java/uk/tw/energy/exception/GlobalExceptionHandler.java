package uk.tw.energy.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API endpoints.
 * Centralizes error handling and provides consistent error responses across all controllers.
 * 
 * All validation failures are logged with unique Error IDs for production debugging and audit trails.
 * 
 * Handles:
 * - MeterNotFoundException (404 Not Found)
 * - PricePlanNotFoundException (404 Not Found)
 * - InvalidMeterReadingException (400 Bad Request)
 * - MethodArgumentNotValidException (400 Bad Request - @Valid constraint violations)
 * - ConstraintViolationException (400 Bad Request - @Validated constraint violations)
 * - Generic exceptions (500 Internal Server Error)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles MeterNotFoundException (smart meter not found or not registered).
     * Returns 404 Not Found with descriptive error response.
     */
    @ExceptionHandler(MeterNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMeterNotFound(MeterNotFoundException ex) {
        logger.info("Meter not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.notFound(ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(errorResponse);
    }

    /**
     * Handles PricePlanNotFoundException (price plan not found).
     * Returns 404 Not Found with descriptive error response.
     */
    @ExceptionHandler(PricePlanNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePricePlanNotFound(PricePlanNotFoundException ex) {
        logger.info("Price plan not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.notFound(ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(errorResponse);
    }

    /**
     * Handles InvalidMeterReadingException (validation or business logic failures).
     * Returns 400 Bad Request with error details.
     */
    @ExceptionHandler(InvalidMeterReadingException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMeterReading(InvalidMeterReadingException ex) {
        logger.warn("Invalid meter reading: {}", ex.details());
        ErrorResponse errorResponse = ErrorResponse.badRequest(ex.getMessage(), ex.details());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse);
    }

    /**
     * Handles MethodArgumentNotValidException (@Valid constraint violations on request body).
     * Extracts field names and constraint messages for client feedback.
     * Returns 400 Bad Request with field-level validation details and error ID for tracking.
     * 
     * Logs: [errorId, field_count, violating_fields]
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> {
                String fieldName = error.getField();
                String message = error.getDefaultMessage();
                Object rejectedValue = error.getRejectedValue();
                
                if (rejectedValue != null) {
                    return String.format("%s: %s (received: '%s')", fieldName, message, rejectedValue);
                }
                return String.format("%s: %s", fieldName, message);
            })
            .collect(Collectors.toList());

        String errorId = generateErrorId();
        String message = String.format("Invalid request data. %d field(s) failed validation. Error ID: %s", 
            details.size(), errorId);
        
        logger.warn("Validation failed [errorId={}] field_count={} fields={}", 
            errorId, details.size(), details);
        
        ErrorResponse errorResponse = ErrorResponse.badRequest(message, details);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse);
    }

    /**
     * Handles ConstraintViolationException (@Validated constraint violations on method parameters).
     * Extracts constraint violation details with property paths for debugging.
     * Returns 400 Bad Request with constraint violation details and error ID for tracking.
     * 
     * Logs: [errorId, violation_count, violating_properties]
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations()
            .stream()
            .map(violation -> {
                String propertyPath = violation.getPropertyPath().toString();
                String message = violation.getMessage();
                Object invalidValue = violation.getInvalidValue();
                
                if (invalidValue != null) {
                    return String.format("%s: %s (received: '%s')", propertyPath, message, invalidValue);
                }
                return String.format("%s: %s", propertyPath, message);
            })
            .collect(Collectors.toList());

        String errorId = generateErrorId();
        String message = String.format("Constraint violation(s) detected. %d violation(s) found. Error ID: %s",
            details.size(), errorId);
        
        logger.warn("Constraint violation [errorId={}] violation_count={} violations={}", 
            errorId, details.size(), details);
        
        ErrorResponse errorResponse = ErrorResponse.badRequest(message, details);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse);
    }

    /**
     * Generic exception handler for unexpected runtime exceptions.
     * Returns 500 Internal Server Error with safe error message (does not expose internals).
     * Logs full exception details for debugging.
     * 
     * Logs: [errorId, exception_type, exception_message, stack_trace]
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String errorId = generateErrorId();
        
        logger.error("Unexpected exception [errorId={}] type={} message={}", 
            errorId, ex.getClass().getSimpleName(), ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.internalServerError(
            String.format("An unexpected error occurred. Please contact support with Error ID: %s", errorId)
        );
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorResponse);
    }

    /**
     * Generates a unique error ID for tracking and debugging.
     * Format: ERR-{UUID shortened to 8 characters}
     * 
     * This ID is included in both the error response and server logs for correlation.
     */
    private String generateErrorId() {
        return "ERR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
