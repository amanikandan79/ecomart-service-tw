package uk.tw.energy.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.List;

/**
 * Standardized error response DTO for all REST API error responses.
 * Provides consistent error information to API clients.
 *
 * @param httpStatus HTTP status code (200-599)
 * @param code unique error code for programmatic handling (e.g., "METER_NOT_FOUND")
 * @param message human-readable error message
 * @param details constraint violation or additional error details (nullable)
 * @param timestamp RFC 3339 timestamp when the error occurred
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int httpStatus,
    String code,
    String message,
    List<String> details,
    Instant timestamp
) {
    /**
     * Factory method for 404 Not Found errors (e.g., unknown meter or price plan).
     */
    public static ErrorResponse notFound(String message) {
        return new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", message, null, Instant.now());
    }

    /**
     * Factory method for 400 Bad Request errors (validation failures).
     */
    public static ErrorResponse badRequest(String message, List<String> details) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", message, details, Instant.now());
    }

    /**
     * Factory method for 400 Bad Request errors with single detail.
     */
    public static ErrorResponse badRequest(String message, String detail) {
        return badRequest(message, detail != null ? List.of(detail) : null);
    }

    /**
     * Factory method for 500 Internal Server Error.
     */
    public static ErrorResponse internalServerError(String message) {
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_SERVER_ERROR", message, null, Instant.now());
    }
}
