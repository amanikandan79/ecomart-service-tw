package uk.tw.energy.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import uk.tw.energy.domain.ElectricityReading;

/**
 * MeterReadingService manages electricity readings for smart meters.
 *
 * <p><b>Validation Strategy</b>:
 * Service-level validation using {@code @Validated} annotation enables method parameter validation.
 * This provides a second line of defense against invalid data reaching business logic.
 *
 * <p><b>Thread Safety Model</b>:
 * This service uses a hybrid approach for thread-safe concurrent access:
 * <ul>
 *   <li>{@link ConcurrentHashMap} for the top-level meter map (meter ID → readings list)</li>
 *   <li>{@link Collections#synchronizedList} for per-meter reading lists</li>
 *   <li>Explicit {@code synchronized} blocks when reading or writing to protect against concurrent modification</li>
 * </ul>
 *
 * <p><b>Important</b>: Read operations return defensive copies ({@link ArrayList}) to prevent external modification of internal state.
 *
 * <p><b>Data Retention Policy</b>: Each meter is capped at {@link #MAX_READINGS_PER_METER} readings.
 * When exceeded, oldest readings are removed (FIFO eviction).
 *
 * @see MeterReadingService#getReadings(String)
 * @see MeterReadingService#storeReadings(String, List)
 */
@Service
@Validated
public class MeterReadingService {

    private static final int MAX_READINGS_PER_METER = 1000;
    private static final Logger logger = LoggerFactory.getLogger(MeterReadingService.class);
    private final ConcurrentMap<String, List<ElectricityReading>> meterAssociatedReadings;

    /**
     * Initializes the service with pre-existing readings (typically from configuration/seeding).
     *
     * @param meterAssociatedReadings initial map of meter ID → readings; converted to thread-safe structures
     */
    public MeterReadingService(Map<String, List<ElectricityReading>> meterAssociatedReadings) {
        this.meterAssociatedReadings = new ConcurrentHashMap<>();
        meterAssociatedReadings.forEach((meterId, readings) -> this.meterAssociatedReadings.put(
                meterId, Collections.synchronizedList(new ArrayList<>(readings))));
    }

    /**
     * Retrieves all electricity readings for a smart meter.
     *
     * <p>Returns a defensive copy to ensure thread safety and prevent external modification.
     *
     * @param smartMeterId the smart meter ID (must not be blank)
     * @return {@link Optional} containing a copy of the readings, or empty if meter has no readings
     * @throws jakarta.validation.ConstraintViolationException if smartMeterId is blank
     */
    public Optional<List<ElectricityReading>> getReadings(@NotBlank String smartMeterId) {
        List<ElectricityReading> readings = meterAssociatedReadings.get(smartMeterId);
        if (readings == null) {
            logger.info("No readings found for meterId={}", smartMeterId);
            return Optional.empty();
        }
        synchronized (readings) {
            logger.debug("Returning {} reading(s) for meterId={}", readings.size(), smartMeterId);
            return Optional.of(new ArrayList<>(readings));
        }
    }

    /**
     * Stores new electricity readings for a smart meter.
     *
     * <p>If the meter does not exist, a new synchronized list is created.
     * Readings are appended; if capacity exceeds {@link #MAX_READINGS_PER_METER},
     * the oldest readings are removed (FIFO eviction).
     *
     * @param smartMeterId the smart meter ID (must not be blank)
     * @param electricityReadings list of readings to store (must not be empty)
     * @throws jakarta.validation.ConstraintViolationException if smartMeterId is blank or electricityReadings is empty
     */
    public void storeReadings(@NotBlank String smartMeterId, @NotEmpty List<ElectricityReading> electricityReadings) {
        List<ElectricityReading> existingReadings = meterAssociatedReadings.computeIfAbsent(
                smartMeterId,
                id -> Collections.synchronizedList(new ArrayList<>())
        );
        synchronized (existingReadings) {
            existingReadings.addAll(electricityReadings);
            int overflow = existingReadings.size() - MAX_READINGS_PER_METER;
            if (overflow > 0) {
                existingReadings.subList(0, overflow).clear();
                logger.warn("Reading buffer trimmed for meterId={} removed={} max={}", smartMeterId, overflow, MAX_READINGS_PER_METER);
            }
        }
        logger.info("Stored {} reading(s) for meterId={} total={}", electricityReadings.size(), smartMeterId, existingReadings.size());
    }
}
