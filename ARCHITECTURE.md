# Architecture

This service is a **single Spring Boot application** with in-memory state seeded at startup.

## Runtime shape

```text
Client
  -> Controllers
      -> Services
          -> In-memory Maps/Lists (seeded beans)
```

## Application layers

1. **Controller layer**
   - `MeterReadingController`
   - `PricePlanComparatorController`
2. **Service layer**
   - `MeterReadingService` (thread-safe meter reading storage with max 1000 readings per meter)
   - `PricePlanService` (cost calculation from kW readings -> kWh)
   - `AccountService` (known smart-meter IDs and plan mapping)
3. **Exception layer**
   - `GlobalExceptionHandler` maps exceptions to JSON error responses
4. **Configuration layer**
   - `SeedingApplicationDataConfiguration` seeds price plans, meter accounts, and initial readings

## Data model

- `MeterReadings` record:
  - `smartMeterId` validated with `@NotBlank`, `@Size(max=64)`, `@Pattern("^[a-zA-Z0-9-]+$")`
  - `electricityReadings` validated with `@NotEmpty`, `@Size(max=1000)`, `@Valid`
- `ElectricityReading` record:
  - `time` with `@NotNull`
  - `reading` with `@NotNull`, `@Positive` (kW)
- `PricePlan` class:
  - supplier name, plan name, unit rate, optional peak multipliers

## Exposed endpoints

1. `POST /readings/store`
2. `GET /readings/read/{smartMeterId}`
3. `GET /price-plans/compare-all/{smartMeterId}`
4. `GET /price-plans/recommend/{smartMeterId}`

## Error contract (actual current response body)

```json
{
  "httpStatus": 400,
  "code": "BAD_REQUEST",
  "message": "Meter reading validation failed",
  "details": [
    "smartMeterId: must not be blank"
  ],
  "timestamp": "2026-06-10T05:30:12.345Z"
}
```

Current code values for `code` are:
- `BAD_REQUEST`
- `NOT_FOUND`
- `INTERNAL_SERVER_ERROR`
