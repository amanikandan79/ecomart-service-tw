# Validation Checklist

- [x] `MeterReadings` record uses Bean Validation annotations
- [x] Nested `ElectricityReading` entries are cascade-validated (`@Valid`)
- [x] Controller validates store payload before service call
- [x] Service layer is annotated with `@Validated`
- [x] Global exception handler returns structured validation errors
- [x] Error codes in current implementation are `BAD_REQUEST`, `NOT_FOUND`, `INTERNAL_SERVER_ERROR`
