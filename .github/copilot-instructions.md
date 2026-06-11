# Copilot Instructions

## Project shape

This is a Spring Boot 3 REST API for smart-meter readings and price-plan comparison. The application is intentionally in-memory: `SeedingApplicationDataConfiguration` provides the `Map` and `List` beans that services consume, so controllers and services are wired around seeded data rather than a database.

## Build, test, and run

- Use the Gradle wrapper.
- Local JDK should match the Gradle toolchain: Java 18+.
- `./gradlew build` compiles, runs tests, and builds the jar.
- `./gradlew test` runs unit tests only.
- `./gradlew functionalTest` runs the functional test source set.
- `./gradlew check` runs unit + functional tests.
- Single unit test: `./gradlew test --tests uk.tw.energy.service.PricePlanServiceTest`
- Single functional test: `./gradlew functionalTest --tests uk.tw.energy.EndpointTest`
- `./gradlew bootRun` starts the app on port 8080.

There is no separate lint task defined. JaCoCo runs after `test` and writes reports under `build/reports/jacoco`.

## Architecture

- `uk.tw.energy.App` is the Spring Boot entry point.
- `controller` exposes two REST areas:
  - `/readings` stores and fetches readings for a smart meter.
  - `/price-plans` compares usage cost across plans and returns cheapest recommendations.
- `service` contains the core logic:
  - `MeterReadingService` stores readings in a `Map<String, List<ElectricityReading>>`.
  - `AccountService` maps smart meter ids to the current plan id.
  - `PricePlanService` calculates usage cost for each plan from stored readings.
- `domain` holds the core data types:
  - `ElectricityReading` and `MeterReadings` are records.
  - `PricePlan` models plan pricing and peak-time multipliers.
- `generator/ElectricityReadingsGenerator` seeds sample readings for startup data.

## Conventions to preserve

- Keep constructor injection; services are built from Spring-managed beans and plain collections.
- Keep the in-memory data model unless the config, tests, and startup wiring are updated together.
- Treat `ElectricityReading.reading` as kW and convert to kWh using elapsed hours in `PricePlanService`.
- Return `400` from `POST /readings/store` for missing/empty meter payloads, and `404` when a meter id is unknown.
- Recommendation results are sorted ascending by cost, and `limit` trims the returned list only when it is smaller than the available results.
- JSON date/time output is configured as ISO strings, not timestamps.
- Unit tests live under `src/test/java`; functional tests live under `src/functional-test/java` and are wired through the custom `functionalTest` Gradle task.
