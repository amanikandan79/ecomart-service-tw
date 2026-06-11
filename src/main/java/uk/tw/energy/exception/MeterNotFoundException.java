package uk.tw.energy.exception;

/**
 * Thrown when a smart meter ID is not found or not registered in the system.
 * This exception signals a 404 Not Found HTTP response.
 *
 * @param meterId the meter ID that was not found
 */
public class MeterNotFoundException extends RuntimeException {
    private final String meterId;

    public MeterNotFoundException(String meterId) {
        super("Smart meter not found: " + meterId);
        this.meterId = meterId;
    }

    public String meterId() {
        return meterId;
    }
}
