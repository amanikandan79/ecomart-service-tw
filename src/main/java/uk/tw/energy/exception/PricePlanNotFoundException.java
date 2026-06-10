package uk.tw.energy.exception;

/**
 * Thrown when a price plan is not found in the system.
 * This exception signals a 404 Not Found HTTP response.
 *
 * @param planId the price plan ID that was not found
 */
public class PricePlanNotFoundException extends RuntimeException {
    private final String planId;

    public PricePlanNotFoundException(String planId) {
        super("Price plan not found: " + planId);
        this.planId = planId;
    }

    public String planId() {
        return planId;
    }
}
