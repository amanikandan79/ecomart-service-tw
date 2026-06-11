# Validation Classification

This document is synchronized with the current codebase as of 2026-06-10.

## Domain-level validation

- `MeterReadings.smartMeterId`
  - `@NotBlank`
  - `@Size(max = 64)`
  - `@Pattern("^[a-zA-Z0-9-]+$")`
- `MeterReadings.electricityReadings`
  - `@NotEmpty`
  - `@Size(max = 1000)`
  - nested `@Valid`
- `ElectricityReading.time`
  - `@NotNull`
- `ElectricityReading.reading`
  - `@NotNull`
  - `@Positive`

## Controller/service validation

- `MeterReadingController` performs manual `SmartValidator` checks and throws `InvalidMeterReadingException` on invalid requests.
- `MeterReadingService` and `PricePlanService` are annotated with `@Validated` and use method-level constraints (for example `@NotBlank`).

## Error handling

`GlobalExceptionHandler` maps to:
- 400 -> `BAD_REQUEST`
- 404 -> `NOT_FOUND`
- 500 -> `INTERNAL_SERVER_ERROR`

Error response body fields:
- `httpStatus`
- `code`
- `message`
- `details`
- `timestamp`
