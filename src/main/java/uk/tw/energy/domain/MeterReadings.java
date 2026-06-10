package uk.tw.energy.domain;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for storing electricity readings.
 * Validates that smartMeterId contains only alphanumeric characters and hyphens (no special characters).
 */
public record MeterReadings(
        @NotBlank 
        @Size(max = 64) 
        @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "must contain only alphanumeric characters and hyphens")
        String smartMeterId,
        @NotEmpty @Size(max = 1000) List<@Valid ElectricityReading> electricityReadings) {

}
