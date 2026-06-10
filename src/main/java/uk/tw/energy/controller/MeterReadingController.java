package uk.tw.energy.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.SmartValidator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.tw.energy.domain.ElectricityReading;
import uk.tw.energy.domain.MeterReadings;
import uk.tw.energy.exception.InvalidMeterReadingException;
import uk.tw.energy.exception.MeterNotFoundException;
import uk.tw.energy.service.AccountService;
import uk.tw.energy.service.MeterReadingService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MeterReadingController handles REST endpoints for storing and retrieving electricity readings.
 *
 * <p><b>Validation Strategy</b>:
 * All incoming requests are validated using Spring Bean Validation:
 * <ul>
 *   <li>{@code smartMeterId}: Must be non-blank and ≤64 characters ({@code @NotBlank}, {@code @Size})</li>
 *   <li>{@code electricityReadings}: Must not be empty and contain ≤1000 entries ({@code @NotEmpty}, {@code @Size})</li>
 *   <li>{@code time}: Must not be null ({@code @NotNull})</li>
 *   <li>{@code reading}: Must be positive (kW) ({@code @NotNull}, {@code @Positive})</li>
 * </ul>
 *
 * <p><b>Authorization Model</b>:
 * This controller assumes all requests are pre-authenticated by an upstream API gateway.
 * Unknown meter IDs (not in {@link AccountService}) are rejected with 404 to prevent unauthorized writes.
 *
 * @see MeterReadings
 * @see ElectricityReading
 */
@RestController
@RequestMapping("/readings")
public class MeterReadingController {

    private static final Logger logger = LoggerFactory.getLogger(MeterReadingController.class);
    private final MeterReadingService meterReadingService;
    private final AccountService accountService;
    private final SmartValidator validator;

    /**
     * Constructs the controller with required dependencies.
     *
     * @param meterReadingService service for managing meter readings
     * @param accountService service for validating known meters
     * @param validator Spring validator for request payload validation
     */
    public MeterReadingController(MeterReadingService meterReadingService, AccountService accountService, SmartValidator validator) {
        this.meterReadingService = meterReadingService;
        this.accountService = accountService;
        this.validator = validator;
    }

    /**
     * Stores electricity readings for a smart meter.
     *
     * <p><b>Returns</b>:
     * <ul>
     *   <li>200 OK: Readings stored successfully</li>
     *   <li>400 Bad Request: Invalid payload (missing meter ID, empty readings list, invalid data)</li>
     *   <li>404 Not Found: Meter ID not recognized (not in registered accounts)</li>
     * </ul>
     *
     * @param meterReadings request payload with meter ID and readings
     * @return ResponseEntity with 200 OK on success
     * @throws InvalidMeterReadingException if request validation fails
     * @throws MeterNotFoundException if meter ID not registered
     */
    @PostMapping("/store")
    public ResponseEntity storeReadings(@RequestBody MeterReadings meterReadings) {
        logger.info("Store readings request received for meterId={}", meterReadings == null ? null : meterReadings.smartMeterId());
        if (meterReadings == null) {
            throw new InvalidMeterReadingException("Request body cannot be null");
        }

        Errors errors = new BeanPropertyBindingResult(meterReadings, "meterReadings");
        validator.validate(meterReadings, errors);
        if (errors.hasErrors()) {
            List<String> details = errors.getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
            throw new InvalidMeterReadingException("Meter reading validation failed", String.join(", ", details));
        }

        if (!accountService.isKnownSmartMeterId(meterReadings.smartMeterId())) {
            logger.warn("Unknown meterId rejected during store request: {}", meterReadings.smartMeterId());
            throw new MeterNotFoundException(meterReadings.smartMeterId());
        }

        meterReadingService.storeReadings(meterReadings.smartMeterId(), meterReadings.electricityReadings());
        logger.info("Stored {} reading(s) for meterId={}", meterReadings.electricityReadings().size(), meterReadings.smartMeterId());
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves all stored readings for a smart meter.
     *
     * <p><b>Returns</b>:
     * <ul>
     *   <li>200 OK: List of readings</li>
     *   <li>404 Not Found: Meter ID not registered or no readings exist</li>
     * </ul>
     *
     * @param smartMeterId the smart meter ID
     * @return ResponseEntity containing the readings on success
     * @throws MeterNotFoundException if meter ID not found
     */
    @GetMapping("/read/{smartMeterId}")
    public ResponseEntity readReadings(@PathVariable String smartMeterId) {
        logger.info("Read readings request received for meterId={}", smartMeterId);
        Optional<List<ElectricityReading>> readings = meterReadingService.getReadings(smartMeterId);
        if (!readings.isPresent()) {
            throw new MeterNotFoundException(smartMeterId);
        }
        logger.info("Returned {} reading(s) for meterId={}", readings.get().size(), smartMeterId);
        return ResponseEntity.ok(readings.get());
    }
}
