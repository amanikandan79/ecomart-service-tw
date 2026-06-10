package uk.tw.energy.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param reading kW
 */
public record ElectricityReading(@NotNull Instant time, @NotNull @Positive BigDecimal reading) {

}
