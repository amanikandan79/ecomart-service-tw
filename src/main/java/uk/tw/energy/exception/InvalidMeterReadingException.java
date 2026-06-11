package uk.tw.energy.exception;

/**
 * Thrown when meter reading data fails validation constraints.
 * This exception signals a 400 Bad Request HTTP response with constraint details.
 *
 * @param details the validation constraint violation details
 */
public class InvalidMeterReadingException extends RuntimeException {
    private final String details;

    public InvalidMeterReadingException(String message, String details) {
        super(message);
        this.details = details;
    }

    public InvalidMeterReadingException(String message) {
        super(message);
        this.details = message;
    }

    public String details() {
        return details;
    }
}
