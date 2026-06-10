package uk.tw.energy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import uk.tw.energy.domain.ElectricityReading;
import uk.tw.energy.domain.PricePlan;

/**
 * PricePlanService calculates energy consumption costs across different price plans.
 *
 * <p><b>Validation Strategy</b>:
 * Service-level validation using {@code @Validated} annotation enables method parameter validation.
 * This provides a second line of defense against invalid data reaching business logic.
 *
 * <p><b>Calculation Model</b>:
 * Converts power readings (kW) into energy cost (currency):
 * <ol>
 *   <li>Calculate average power consumption across all readings (kW)</li>
 *   <li>Calculate elapsed time from first to last reading (hours)</li>
 *   <li>Derive energy consumed: kW × hours = kWh</li>
 *   <li>Multiply by plan's unit rate to get total cost</li>
 * </ol>
 *
 * <p><b>Precision</b>:
 * <ul>
 *   <li>Energy calculation uses 6 decimal places (BigDecimal with HALF_UP rounding)</li>
 *   <li>Final cost rounded to 2 decimal places (currency precision)</li>
 * </ul>
 *
 * <p><b>Edge Cases</b>:
 * <ul>
 *   <li>Fewer than 2 readings: Returns 0 (cannot calculate elapsed time)</li>
 *   <li>All readings at same timestamp: Returns 0 (no time elapsed)</li>
 *   <li>Empty readings: Returns 0</li>
 * </ul>
 *
 * @see PricePlanService#getConsumptionCostOfElectricityReadingsForEachPricePlan(String)
 * @see PricePlanService#calculateCost(List, PricePlan)
 */
@Service
@Validated
public class PricePlanService {

    private final List<PricePlan> pricePlans;
    private final MeterReadingService meterReadingService;

    /**
     * Constructs the service with price plans and meter reading data.
     *
     * @param pricePlans list of available pricing plans
     * @param meterReadingService service providing meter readings
     */
    public PricePlanService(List<PricePlan> pricePlans, MeterReadingService meterReadingService) {
        this.pricePlans = pricePlans;
        this.meterReadingService = meterReadingService;
    }

    /**
     * Calculates the consumption cost for each available price plan given a meter's readings.
     *
     * @param smartMeterId the smart meter ID (must not be blank)
     * @return Optional map of plan names to costs; empty if meter has no readings
     * @throws jakarta.validation.ConstraintViolationException if smartMeterId is blank
     */
    public Optional<Map<String, BigDecimal>> getConsumptionCostOfElectricityReadingsForEachPricePlan(@NotBlank String smartMeterId) {
        Optional<List<ElectricityReading>> electricityReadings = meterReadingService.getReadings(smartMeterId);

        if (!electricityReadings.isPresent()) {
            return Optional.empty();
        }

        return Optional.of(pricePlans.stream().collect(
                Collectors.toMap(PricePlan::getPlanName, t -> calculateCost(electricityReadings.get(), t))));
    }

    /**
     * Calculates the cost for a specific price plan given a list of readings.
     *
     * <p>Formula: (average kW) × (elapsed hours) × (unit rate) = cost (rounded to 2 decimals)
     *
     * @param electricityReadings list of readings to analyze
     * @param pricePlan the pricing plan to apply
     * @return calculated cost (BigDecimal with 2 decimal places)
     */
    private BigDecimal calculateCost(List<ElectricityReading> electricityReadings, PricePlan pricePlan) {
        BigDecimal average = calculateAverageReading(electricityReadings);
        BigDecimal timeElapsed = calculateTimeElapsed(electricityReadings);

        if (timeElapsed.compareTo(BigDecimal.ZERO) == 0) {
            // Not enough elapsed time to compute a meaningful cost
            return BigDecimal.ZERO;
        }

        // average: kW (power), timeElapsed: hours -> energy in kWh = power * time
        BigDecimal energyKWh = average.multiply(timeElapsed).setScale(6, RoundingMode.HALF_UP);
        BigDecimal cost = energyKWh.multiply(pricePlan.getUnitRate());
        // Round to two decimals for currency precision
        return cost.setScale(2, RoundingMode.HALF_UP);
    }

    private static final BigDecimal AVERAGE_MULTIPLIER = BigDecimal.ONE;

    /**
     * Calculates the average power consumption (kW) from readings.
     *
     * @param electricityReadings list of readings
     * @return average kW (or 0 if no readings)
     */
    private BigDecimal calculateAverageReading(List<ElectricityReading> electricityReadings) {
        if (electricityReadings == null || electricityReadings.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal summedReadings = electricityReadings.stream()
                .map(ElectricityReading::reading)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal average = summedReadings.divide(BigDecimal.valueOf(electricityReadings.size()), 6, RoundingMode.HALF_UP);
        // Multiplier preserved from original logic; review if business rule requires it.
        return average.multiply(AVERAGE_MULTIPLIER);
    }

    /**
     * Calculates the elapsed time (in hours) from first to last reading.
     *
     * @param electricityReadings list of readings (must be sorted by time)
     * @return elapsed time in hours (or 0 if insufficient data)
     */
    private BigDecimal calculateTimeElapsed(List<ElectricityReading> electricityReadings) {
        if (electricityReadings == null || electricityReadings.size() < 2) {
            // Not enough data points to compute an elapsed time in hours
            return BigDecimal.ZERO;
        }

        ElectricityReading first = electricityReadings.stream()
                .min(Comparator.comparing(ElectricityReading::time))
                .get();

        ElectricityReading last = electricityReadings.stream()
                .max(Comparator.comparing(ElectricityReading::time))
                .get();

        long seconds = Duration.between(first.time(), last.time()).getSeconds();
        if (seconds <= 0) {
            return BigDecimal.ZERO;
        }

        // Convert seconds to hours using BigDecimal to avoid floating point precision issues
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP);
    }

}
