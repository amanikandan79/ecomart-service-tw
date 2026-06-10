# Validation Quick Reference

## Core constraints

| Location | Rule |
|---|---|
| `MeterReadings.smartMeterId` | non-blank, max 64 chars, alphanumeric + hyphen only |
| `MeterReadings.electricityReadings` | required, max 1000 entries |
| `ElectricityReading.time` | required |
| `ElectricityReading.reading` | required, positive |

## Runtime behavior

- Invalid request payloads return `400 BAD_REQUEST`.
- Unknown meters return `404 NOT_FOUND`.
- Unhandled exceptions return `500 INTERNAL_SERVER_ERROR`.

## Source of truth

Validation behavior is implemented in:
- `src/main/java/uk/tw/energy/domain/*.java`
- `src/main/java/uk/tw/energy/controller/MeterReadingController.java`
- `src/main/java/uk/tw/energy/service/*.java`
- `src/main/java/uk/tw/energy/exception/GlobalExceptionHandler.java`
