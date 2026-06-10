# Architecture & Implementation Guide

## Overview

JOI Energy is a Spring Boot REST API for smart meter data collection and energy price plan comparison. The architecture emphasizes simplicity with in-memory state management suitable for small deployments.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    External API Gateway (auth/TLS)              │
└──────────────────────────────────────┬──────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────┐
│                      Spring Boot Application                     │
├──────────────────────────────────────────────────────────────────┤
│  Controllers (HTTP Layer) - 5 REST Endpoints                    │
│  ├── MeterReadingController:                                    │
│  │   ├── POST /readings/store - Store electricity readings      │
│  │   └── GET /readings/read/{smartMeterId} - Get readings       │
│  ├── PricePlanComparatorController:                             │
│  │   ├── GET /price-plans - List all price plans               │
│  │   ├── GET /price-plans/compare-all/{smartMeterId} - Costs   │
│  │   └── GET /price-plans/recommend/{smartMeterId} - Recommend │
├──────────────────────────────────────────────────────────────────┤
│  Services (Business Logic)                                      │
│  ├── MeterReadingService: Manages meter readings (in-memory)    │
│  ├── PricePlanService: Calculates energy costs                  │
│  └── AccountService: Maps meters to price plans                 │
├──────────────────────────────────────────────────────────────────┤
│  Domain Models (Records)                                        │
│  ├── MeterReadings: Request payload with validation             │
│  ├── ElectricityReading: Power readings (kW)                    │
│  ├── PricePlan: Pricing rules and peak multipliers              │
│  └── PeakTimeMultiplier: Nested pricing multiplier by day       │
├──────────────────────────────────────────────────────────────────┤
│  In-Memory State                                                │
│  ├── ConcurrentHashMap<String, List<ElectricityReading>>        │
│  └── Map<String, String> (smart meter → price plan)             │
└──────────────────────────────────────────────────────────────────┘
```

## Layering & Responsibilities

### Controller Layer (HTTP Boundary)
**Files**: `MeterReadingController.java`, `PricePlanComparatorController.java`

Responsibilities:
- Accept HTTP requests and validate JSON payloads
- Map URL paths and query parameters
- Return appropriate HTTP status codes (200, 400, 404)
- Delegate to services for business logic

**Key Decisions**:
- No Spring Security annotations; assumes upstream authentication
- Input validation via Spring Bean Validation (`@NotBlank`, `@Positive`, `@Pattern`, etc.)
- Unknown meter IDs rejected with 404 (prevents data leakage)

#### REST API Endpoints

**1. Store Readings**
```
POST /readings/store
Content-Type: application/json

Request Body:
{
  "smartMeterId": "smart-meter-0",
  "electricityReadings": [
    { "time": 1633104000000, "value": 25.5 },
    { "time": 1633107600000, "value": 26.0 }
  ]
}

Response (200 OK): 
(empty body)

Error Responses:
- 400 Bad Request: Invalid payload (validation failure)
  Response: { "errorId": "ERR-...", "code": "VALIDATION_ERROR", "details": [...] }
- 404 Not Found: Meter ID not registered
  Response: { "errorId": "ERR-...", "code": "METER_NOT_FOUND", "message": "..." }
```

**Validation Rules**:
- `smartMeterId`: @NotBlank, @Size(max=64), @Pattern(only alphanumeric + hyphens)
- `electricityReadings`: @NotEmpty, @Size(max=1000)
- Each reading `time`: @NotNull (milliseconds since epoch)
- Each reading `value`: @NotNull, @Positive (kW, must be > 0)

**Error Scenarios**:
| Scenario | HTTP Code | Error Code | Message |
|----------|-----------|-----------|---------|
| Blank smartMeterId | 400 | VALIDATION_ERROR | smartMeterId: must not be blank |
| Empty readings list | 400 | VALIDATION_ERROR | electricityReadings: must not be empty |
| Special chars in meter ID | 400 | VALIDATION_ERROR | smartMeterId: must contain only alphanumeric... |
| Unknown meter | 404 | METER_NOT_FOUND | Smart meter not found: {id} |
| Null reading time | 400 | VALIDATION_ERROR | time: must not be null |
| Negative reading value | 400 | VALIDATION_ERROR | reading: must be greater than 0 |

---

**2. Get Readings**
```
GET /readings/read/{smartMeterId}

Path Parameters:
- smartMeterId: The smart meter ID (alphanumeric + hyphens, max 64 chars)

Response (200 OK):
[
  { "time": 1633104000000, "value": 25.5 },
  { "time": 1633107600000, "value": 26.0 }
]

Error Responses:
- 404 Not Found: Meter not found or no readings stored
  Response: { "errorId": "ERR-...", "code": "METER_NOT_FOUND", "message": "..." }
```

**Validation**:
- `smartMeterId`: @NotBlank, @Size(max=64), @Pattern (alphanumeric + hyphens only)

**Behavior**:
- Returns defensive copy of readings (thread-safe, immutable for caller)
- Returns empty list if meter has no readings (**BEHAVIOR**: Currently returns 404; should return 200 OK with empty array for REST best practices)
- Readings ordered by timestamp (insertion order)

**Error Scenarios**:
| Scenario | HTTP Code | Error Code | Message |
|----------|-----------|-----------|---------|
| Unknown meter ID | 404 | METER_NOT_FOUND | Meter not found: {id} |
| Meter registered but no readings | 404 | METER_NOT_FOUND | No readings for meter {id} |
| Invalid meter ID format | 400 | VALIDATION_ERROR | smartMeterId: must contain only alphanumeric... |

**Note on 404 Response**: Currently returns 404 even if meter is registered but has no readings. Consider returning:
```json
{
  "statusCode": 200,
  "readings": []
}
```
For REST API best practices (2xx for success even with empty data).

---

**3. Get All Price Plans**
```
GET /price-plans
(No parameters required)

Response (200 OK):
[
  {
    "planId": "price-plan-0",
    "planName": "Esme Rate 2",
    "peakTimeMultipliers": [
      { "day": "MONDAY", "multiplier": 1.0 },
      { "day": "TUESDAY", "multiplier": 1.0 },
      ...
    ]
  },
  ...
]
```

---

**4. Compare Costs (All Price Plans)**
```
GET /price-plans/compare-all/{smartMeterId}

Path Parameters:
- smartMeterId: The smart meter ID (alphanumeric + hyphens, max 64 chars)

Response (200 OK):
{
  "pricePlanId": "price-plan-0",
  "pricePlanComparisons": {
    "price-plan-0": 0.0002,
    "price-plan-1": 0.0003,
    "price-plan-2": 0.0004
  }
}

Error Responses:
- 404 Not Found: Meter not registered OR meter has no readings
  Response: { "errorId": "ERR-...", "code": "METER_NOT_FOUND", "message": "..." }
- 400 Bad Request: Invalid meter ID format
  Response: { "errorId": "ERR-...", "code": "VALIDATION_ERROR", "message": "..." }
```

**Validation**:
- `smartMeterId`: @NotBlank, @Size(max=64), @Pattern (alphanumeric + hyphens only)

**Calculation Process**:
1. Verify meter is registered (exists in AccountService)
2. Verify meter has readings (MeterReadingService.getReadings() returns non-empty)
3. For each PricePlan:
   - Calculate average kW from readings
   - Calculate elapsed time in hours
   - Multiply: avg kW × hours × unit rate
   - Round to 2 decimals (currency precision)
4. Return map of plan IDs to costs

**Error Scenarios**:
| Scenario | HTTP Code | Error Code | Message | Support Notes |
|----------|-----------|-----------|---------|---------------|
| Unknown meter | 404 | METER_NOT_FOUND | Meter not found: {id} | Check if meter is registered in AccountService |
| Meter has no readings | 404 | METER_NOT_FOUND | No readings for meter {id} | Store readings first with POST /readings/store |
| Invalid meter format | 400 | VALIDATION_ERROR | smartMeterId: must contain only alphanumeric... | Remove special chars, use only a-z, A-Z, 0-9, hyphens |
| Only 1 reading | 200 OK | N/A | Returns cost = 0 | Normal; need >= 2 readings with time diff for accurate calc |
| All readings same timestamp | 200 OK | N/A | Returns cost = 0 | Normal; no time elapsed = no cost. Add readings with different times. |

**Note on Error Code Ambiguity**: Currently returns 404 for both "meter not registered" AND "meter has no readings". Consider:
- Error Code: METER_NOT_REGISTERED (404) - meter not in system
- Error Code: NO_READINGS_FOUND (200 OK, empty results) - meter registered but no data

---

**5. Get Recommended Price Plans**
```
GET /price-plans/recommend/{smartMeterId}?limit=2

Path Parameters:
- smartMeterId: The smart meter ID (alphanumeric + hyphens, max 64 chars)

Query Parameters:
- limit: (optional) Number of plans to return (must be > 0). 
  If omitted, returns all plans sorted by cost.

Response (200 OK):
[
  { "key": "price-plan-2", "value": 0.0002 },
  { "key": "price-plan-1", "value": 0.0003 }
]
(Sorted by cost ascending, most affordable first)

Error Responses:
- 404 Not Found: Meter not registered OR no readings
  Response: { "errorId": "ERR-...", "code": "METER_NOT_FOUND", "message": "..." }
- 400 Bad Request: Invalid limit or meter ID
  Response: { "errorId": "ERR-...", "code": "VALIDATION_ERROR", "message": "..." }
```

**Validation**:
- `smartMeterId`: @NotBlank, @Size(max=64), @Pattern (alphanumeric + hyphens only)
- `limit`: @Positive (if provided, must be > 0)

**Behavior**:
- Returns all prices sorted by cost (ascending) if limit not provided
- Returns top `limit` cheapest plans if limit is provided
- Plans with same cost may be in any order (depends on HashMap iteration)

**Error Scenarios**:
| Scenario | HTTP Code | Error Code | Message |
|----------|-----------|-----------|---------|
| Unknown meter | 404 | METER_NOT_FOUND | Meter not found: {id} |
| No readings | 404 | METER_NOT_FOUND | No readings for meter {id} |
| Invalid limit (0 or negative) | 400 | VALIDATION_ERROR | limit must be greater than 0 |
| Invalid meter ID format | 400 | VALIDATION_ERROR | smartMeterId: must contain only alphanumeric... |

**Example Calls**:
```bash
# Get top 2 cheapest plans
curl "http://localhost:8080/price-plans/recommend/smart-meter-0?limit=2"

# Get all plans sorted by cost
curl "http://localhost:8080/price-plans/recommend/smart-meter-0"

# Valid meter ID formats
curl "http://localhost:8080/price-plans/recommend/SM-001"
curl "http://localhost:8080/price-plans/recommend/meter0"
curl "http://localhost:8080/price-plans/recommend/10101010"

# Invalid meter ID formats (will be rejected)
curl "http://localhost:8080/price-plans/recommend/smart_meter_0"    # underscore
curl "http://localhost:8080/price-plans/recommend/smart-meter@0"    # @
curl "http://localhost:8080/price-plans/recommend/smart meter 0"    # space
```

### Service Layer (Business Logic)
**Files**: `MeterReadingService.java`, `PricePlanService.java`, `AccountService.java`

#### MeterReadingService
- Thread-safe management of electricity readings per meter
- Hybrid concurrency model: `ConcurrentHashMap` + `Collections.synchronizedList`
- FIFO eviction: bounded to 1000 readings per meter
- Defensive copies on reads to prevent external modification

#### PricePlanService
- Calculates energy cost from power readings
- Formula: `(average kW) × (elapsed hours) × (unit rate) = cost (2 decimals)`
- Handles edge cases: insufficient data, zero time elapsed
- Uses `BigDecimal` for financial precision (no floating-point errors)

#### AccountService
- Maintains mapping of smart meters to price plans
- Validates meter ID existence before allowing writes

### Domain Layer (Data Models)
**Files**: All Java records in `domain/` package

- **MeterReadings**: Request payload
  - `@NotBlank String smartMeterId` (≤64 chars)
  - `@NotEmpty List<@Valid ElectricityReading> electricityReadings` (≤1000 items)
- **ElectricityReading**: Single power reading
  - `@NotNull Instant time`
  - `@NotNull @Positive BigDecimal reading` (in kW)
- **PricePlan**: Pricing configuration
  - Immutable; list of peak multipliers is defensive-copied
- **PeakTimeMultiplier**: Day-of-week multiplier for peak pricing

## Thread Safety Model

### Why Hybrid Concurrency?

The application uses a mixed approach to balance simplicity with safety:

1. **ConcurrentHashMap** for top-level meter map
   - Safe concurrent insertion/lookup of new meters
   - No explicit locking on map operations
2. **Collections.synchronizedList** for per-meter readings
   - Ensures list modifications are atomic
   - All threads see consistent updates
3. **Explicit synchronized blocks** on per-meter lists
   - Protects compound operations (read-copy, read-size-check-modify)
   - Prevents concurrent modification during iteration

### Example: Safe Read

```java
public Optional<List<ElectricityReading>> getReadings(String smartMeterId) {
    List<ElectricityReading> readings = meterAssociatedReadings.get(smartMeterId);
    if (readings == null) {
        return Optional.empty();
    }
    synchronized (readings) {
        return Optional.of(new ArrayList<>(readings));  // Defensive copy
    }
}
```

Why defensive copy?
- Prevents caller from modifying internal state
- Ensures consistency snapshot at read time
- Allows safe iteration outside locked region

### Known Limitation: Not Suitable for Multi-Instance

The in-memory store is **not shared across instances**. If deployed with multiple pods/instances behind a load balancer:
- Each instance maintains separate state
- Reads may return inconsistent data depending on routing
- **Solution**: Use external persistence (PostgreSQL, Redis) + distributed cache invalidation

## Data Flow: Store Readings

```
1. POST /readings/store { smartMeterId, electricityReadings }
   │
2. MeterReadingController validates payload
   ├─ Check null
   ├─ Bean Validation (@NotBlank, @Positive, @Size, @Pattern)
   ├─ Check meter is known (AccountService)
   │
3. MeterReadingService.storeReadings(smartMeterId, readings)
   ├─ Get or create synchronized list for meter
   ├─ Lock & append readings
   ├─ If > 1000, remove oldest (FIFO eviction)
   │
4. Return 200 OK
```

## Data Flow: Get Readings

```
1. GET /readings/read/{smartMeterId}
   │
2. MeterReadingController validates smartMeterId
   ├─ @NotBlank, @Size(max=64), @Pattern validation
   │
3. MeterReadingService.getReadings(smartMeterId)
   ├─ Lookup meter in ConcurrentHashMap
   ├─ If exists:
   │  ├─ Synchronize on meter's readings list
   │  ├─ Create defensive copy (new ArrayList)
   │  └─ Return Optional with copy
   ├─ If not found:
   │  └─ Return Optional.empty()
   │
4. If empty:
   ├─ Throw MeterNotFoundException (404)
   │
5. If readings exist:
   ├─ Return 200 OK with readings array
```

## Data Flow: Get All Price Plans

```
1. GET /price-plans
   │
2. Return all registered price plans with peak multipliers
   │
3. Return 200 OK with array of plans
```

## Data Flow: Compare All Price Plans

```
1. GET /price-plans/compare-all/{smartMeterId}
   │
2. PricePlanComparatorController validates smartMeterId
   ├─ @NotBlank, @Size(max=64), @Pattern validation
   ├─ Check if meter is registered (AccountService.getPricePlanIdForSmartMeterId)
   │
3. If meter not registered:
   ├─ Throw MeterNotFoundException (404)
   │
4. PricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan()
   ├─ For each price plan:
   │  ├─ Get readings snapshot from MeterReadingService
   │  ├─ If empty readings:
   │  │  └─ Return empty Optional (no cost data)
   │  ├─ Calculate average power (sum / count)
   │  ├─ Calculate elapsed hours (max time - min time) / 3600000ms
   │  ├─ Get peak multiplier for current day
   │  ├─ Calculate cost = avg power × hours × rate × multiplier
   │  └─ Add to result map
   │
5. If no readings:
   ├─ Throw MeterNotFoundException (404)
   │
6. Return 200 OK with price plan ID and cost comparisons
```

## Data Flow: Get Recommended Price Plans (Cheapest)

```
1. GET /price-plans/recommend/{smartMeterId}?limit=2
   │
2. PricePlanComparatorController validates
   ├─ smartMeterId: @NotBlank, @Size, @Pattern validation
   ├─ limit: @Positive validation (if provided)
   │
3. PricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan()
   ├─ (Same as compare-all: calculate costs for all plans)
   │
4. Sort results by cost (ascending)
   │
5. If limit provided:
   ├─ Return only top `limit` results
   │
6. Return 200 OK with recommended plans (cheapest first)
```

## Input Validation

All user inputs are validated using Spring Bean Validation:

| Field | Constraints | HTTP Status on Fail |
|-------|-------------|-------------------|
| `smartMeterId` | @NotBlank, @Size(max=64) | 400 Bad Request |
| `electricityReadings` | @NotEmpty, @Size(max=1000) | 400 Bad Request |
| `reading` (kW) | @NotNull, @Positive | 400 Bad Request |
| `time` (Instant) | @NotNull | 400 Bad Request |
| Known meter ID? | AccountService lookup | 404 Not Found |

## Error Handling

| Scenario | HTTP Status | Reason |
|----------|------------|--------|
| Null/malformed payload | 400 Bad Request | Request validation failure |
| Unknown meter on POST | 404 Not Found | Prevent writes to unregistered meters |
| Unknown meter on GET | 404 Not Found | No data for unregistered meter |
| Invalid limit (≤ 0) | 400 Bad Request | Cannot return <= 0 results |
| Insufficient readings | 200 OK | Returns cost of 0 (valid) |

## Security Assumptions

### Authentication & Authorization
- **No built-in auth**: API assumes all requests are pre-authenticated by an upstream gateway
- **Smart meter validation**: Only known meters (registered in `AccountService`) can write data
- **No user context**: API is meter-centric, not user-centric

### Data Protection
- **No encryption**: API handles unencrypted readings; TLS enforced at gateway/load balancer
- **No secrets in code**: No hardcoded API keys or credentials
- **No sensitive logging**: Error messages don't leak internal state (by design)

### Recommended Deployment Posture

```
Internet → TLS Termination
        → Load Balancer
        → API Gateway (Authentication/Authorization/Rate Limiting)
        → Spring Boot Instance (This API)
```

## Deployment Considerations

### Single-Instance (Development/Test)
- In-memory store is sufficient
- No external dependencies required
- Data lost on restart

### Multi-Instance (Production)
**Required changes**:
1. Replace in-memory store with external persistence (PostgreSQL, Redis)
2. Add distributed cache invalidation or event-driven updates
3. Configure database connection pooling
4. Add structured logging + centralized log aggregation
5. Add metrics (Micrometer) for monitoring

### Scalability Limits

| Scenario | Current Limit | Impact |
|----------|--------------|--------|
| Readings per meter | 1000 | Older readings evicted (FIFO) |
| Concurrent requests | Thread pool (default 10-200) | Queue/reject under load |
| Total in-memory footprint | ~100 meters × 1000 readings × 64 bytes ≈ 6.4 MB | Manageable; consider external store for production |

## Testing Strategy

### Unit Tests
- **Controllers**: Direct invocation, mock services
- **Services**: Test business logic (cost calculations, boundary conditions)
- **Records**: Serialization/deserialization with validation

### Integration Tests
- **Functional tests**: Full Spring Boot context, TestRestTemplate
- **Coverage**: 70-75% line coverage; gaps in concurrency and edge cases

### Recommended Additions (Phase 2)
- [ ] Concurrency tests: Verify thread safety under concurrent load
- [ ] Edge case tests: Zero readings, negative prices, max precision decimals
- [ ] Load tests: Verify behavior under expected QPS and memory limits

## Configuration

### In `build.gradle.kts`
- Java toolchain: 18+
- Dependencies: Spring Boot 3.1.4, Spring Validation, JUnit 5
- Jacoco coverage reporting

### In `sonar-project.properties` (if running SonarQube)
- Project key, source/test paths, coverage reports

### In `application.properties` (if needed)
- Port: 8080 (default)
- Logging level: INFO (default)

## Monitoring & Observability (Future)

To move from 7.8/10 → 8.5/10 production readiness:

1. **Structured Logging** (SLF4J + logback-spring.xml)
   - Log key operations (store, calculate, errors)
   - Include correlation IDs for tracing

2. **Metrics** (Micrometer)
   - Request count/latency (auto-wired by Spring)
   - Custom: readings stored per meter, cost calculation time

3. **Health Checks** (Spring Boot Actuator)
   - `/actuator/health`: Overall app status
   - `/actuator/health/readiness`: Ready to serve requests?

4. **Alerting**
   - High error rate (> 1% 4xx/5xx)
   - Memory usage > 80%
   - Response time > 500ms (p99)

## Quick Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| 404 on unknown meter POST | Meter not in AccountService | Register in `SeedingApplicationDataConfiguration.java` |
| 400 Bad Request on valid JSON | Validation constraint violated | Check @NotBlank, @Positive, @Size constraints |
| Cost always 0 | Fewer than 2 readings or same timestamp | Add more readings spanning time |
| Data lost on restart | In-memory store | Expected; use external DB for persistence |

---

**Last Updated**: 2026-06-09  
**Status**: Production-ready after Phase 2 completions  
**Quality Score**: 7.8/10 (improving to 8.5/10 with logging + concurrency tests)
