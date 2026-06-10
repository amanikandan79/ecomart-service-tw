# Deployment Guide

## Scope

Deploy the current Spring Boot service jar to an environment with Java 18+.

## 1. Prerequisites

- Java 18 or newer
- Network access to target host
- Gradle wrapper available in repository

## 2. Build

### Windows

```powershell
.\gradlew.bat clean build
```

### macOS/Linux

```bash
./gradlew clean build
```

Build output jar: `build/libs/`

## 3. Test expectations before deploy

- `test` task: passing in current repo state
- `functionalTest` task: currently failing due invalid package declaration in `EndpointTest` (`package java.uk.tw.energy`)

## 4. Run

### Windows

```powershell
java -jar .\build\libs\*.jar
```

### macOS/Linux

```bash
java -jar build/libs/*.jar
```

Default port: `8080`

## 5. Health and smoke checks

```bash
curl http://localhost:8080/price-plans/compare-all/smart-meter-0
curl http://localhost:8080/price-plans/recommend/smart-meter-0?limit=2
```

## 6. Error model reference

Error body fields:
- `httpStatus`
- `code` (`BAD_REQUEST`, `NOT_FOUND`, `INTERNAL_SERVER_ERROR`)
- `message`
- `details`
- `timestamp`

## 7. Rollback

1. Stop current process.
2. Start previous known-good jar.
3. Re-run smoke checks.
