# JOI Energy Service

Spring Boot 3 REST API for smart-meter readings and price-plan cost comparison.

## Current state (synced: 2026-06-10)

- In-memory application (no database).
- 4 active API endpoints (`/readings/*`, `/price-plans/*`).
- Standardized error body: `httpStatus`, `code`, `message`, `details`, `timestamp`.
- Unit tests pass (`26` tests).
- `functionalTest` currently fails due `src/functional-test/java/uk/tw/energy/EndpointTest.java` package declaration (`package java.uk.tw.energy;`), which triggers `SecurityException: Prohibited package name`.

## Build, test, run

### Windows

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat functionalTest
.\gradlew.bat bootRun
```

### macOS/Linux

```bash
./gradlew clean build
./gradlew test
./gradlew functionalTest
./gradlew bootRun
```

Java toolchain is set to **Java 18** in `build.gradle.kts`.

## Key components

- `uk.tw.energy.App` - Spring Boot entry point
- `uk.tw.energy.SeedingApplicationDataConfiguration` - seeded in-memory beans
- `controller` - HTTP APIs
- `service` - business logic (`MeterReadingService`, `PricePlanService`, `AccountService`)
- `exception` - centralized error handling (`GlobalExceptionHandler`)

## API overview

1. `POST /readings/store`
2. `GET /readings/read/{smartMeterId}`
3. `GET /price-plans/compare-all/{smartMeterId}`
4. `GET /price-plans/recommend/{smartMeterId}?limit={n}`

See `API_REFERENCE.md` for request/response details and `ARCHITECTURE.md` for flow/design.
