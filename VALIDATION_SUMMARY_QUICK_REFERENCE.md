# Validation Classification - Quick Reference

## All Validation Layers Classified

### 📊 LAYER-BY-LAYER BREAKDOWN

#### LAYER 1: DOMAIN (Input Constraints)
**File**: `src/main/java/uk/tw/energy/domain/`

**MeterReadings.java**
```
@NotBlank String smartMeterId           → Field must not be blank
@Size(max=64) smartMeterId              → Field max 64 characters
@NotEmpty List<...> electricityReadings → List must have elements
@Size(max=1000) electricityReadings     → List max 1000 items
@Valid electricityReadings[*]           → Cascade validation to nested objects
```

**ElectricityReading.java**
```
@NotNull Instant time                   → Field must not be null
@NotNull BigDecimal reading             → Field must not be null
@Positive BigDecimal reading            → Value must be > 0 (business rule: kW > 0)
```

**PricePlan.java**
```
Collections.unmodifiableList()          → Data integrity (immutability)
Null fallback to empty list             → Null-safety
```

---

#### LAYER 2: CONTROLLER (Request Boundary)
**File**: `src/main/java/uk/tw/energy/controller/`

**MeterReadingController.java**
```
@RestController                         → Spring annotation for REST handler
@RequestMapping("/readings")            → Base URL path
@PostMapping("/store")                  → HTTP POST endpoint
@RequestBody MeterReadings              → Deserialize JSON to record

Validation Flow:
  1. if (meterReadings == null) → 400
  2. SmartValidator.validate()  → 400 (constraints checked)
  3. accountService.isKnownSmartMeterId() → 404 (authorization)
  4. meterReadingService.storeReadings() → 200
```

**PricePlanComparatorController.java**
```
@GetMapping("/recommend/{smartMeterId}")
@PathVariable String smartMeterId       → Extract path parameter
@RequestParam(value="limit") Integer limit → Extract query parameter

Validation:
  if (limit != null && limit <= 0) → 400
  if (!consumptionsForPricePlans.isPresent()) → 404
```

---

#### LAYER 3: SERVICE (Business Logic)
**File**: `src/main/java/uk/tw/energy/service/`

**MeterReadingService.java**
```
@Service                                → Spring component annotation
ConcurrentHashMap                       → Thread-safe map (no race conditions)
Collections.synchronizedList()          → Synchronized per-meter list
synchronized (existingReadings)         → Explicit lock for compound operations

Validations:
  if (readings == null)                 → Null-safety
  new ArrayList<>(readings)             → Defensive copy (prevent external modification)
  if (overflow > 0)                     → Capacity check (1000 readings/meter)
  existingReadings.subList(0, overflow).clear() → FIFO eviction
```

**PricePlanService.java**
```
@Service                                → Spring component annotation

Validations:
  if (electricityReadings == null || isEmpty()) → 0 (no data)
  if (size < 2)                         → 0 (insufficient data points)
  if (timeElapsed == 0)                 → 0 (no time elapsed)
  BigDecimal.setScale(6, HALF_UP)       → Precision (energy calculation)
  BigDecimal.setScale(2, HALF_UP)       → Precision (currency result)
```

**AccountService.java**
```
@Service                                → Spring component annotation

Validation:
  isKnownSmartMeterId(smartMeterId)     → Authorization (meter whitelist)
```

---

#### LAYER 4: PERSISTENCE (Storage & Integrity)
**File**: In-memory storage in MeterReadingService

```
Data Structure:
  ConcurrentMap<String, List<ElectricityReading>>
    └─ Maps meter ID to synchronized list of readings

Validations:
  synchronized (existingReadings)       → Thread-safety on reads/writes
  new ArrayList<>(readings)             → Defensive copy (immutability)
  1000 reading cap per meter            → Bounded memory (FIFO eviction)
```

---

#### LAYER 5: GLOBAL EXCEPTION HANDLING ❌ MISSING
**Status**: Not implemented; returns generic 400/404 with no details

**What's Missing**:
- ❌ @RestControllerAdvice (centralized exception handler)
- ❌ Domain-specific exceptions (MeterNotFoundException, InvalidMeterReadingException)
- ❌ ErrorResponse DTO (consistent error format)
- ❌ Audit logging on validation failures

**Impact**:
- Client receives generic HTTP status only
- Cannot determine which field failed validation
- No audit trail for debugging

**Recommendation**: Implement in Phase 1 (1-2 hours)

---

### 🏷️ SPRING BOOT ANNOTATIONS BY FUNCTION

#### Dependency Injection & Component Registration
```
@RestController         → REST endpoint handler (+ @ResponseBody)
@Service                → Business logic component
@Component              → Generic Spring-managed component
@Configuration          → Bean factory configuration
@RestControllerAdvice   → Global exception handler (NEEDS TO BE ADDED)
```

#### HTTP Mapping & Routing
```
@RequestMapping("/path")            → Base URI for controller
@PostMapping("/path")               → HTTP POST handler
@GetMapping("/path")                → HTTP GET handler
@PathVariable String param          → Extract URL path parameter
@RequestParam String param          → Extract query string parameter
@RequestBody Object payload         → Deserialize JSON body
```

#### Input Validation (Jakarta Bean Validation)
```
@NotBlank String field                      → String not null/empty/whitespace
@NotEmpty Collection field                  → Collection not null/empty
@NotNull Object field                       → Object not null
@Positive BigDecimal field                  → Number > 0
@Size(min=X, max=Y) String/List field       → Min/max length or size
@Valid Collection<@Valid Type> field        → Cascade validation to nested
@Pattern(regexp="...") String field         → Regex match (future use)
@Email String field                         → Email format (future use)
```

#### Exception Handling (NEEDS IMPLEMENTATION)
```
@ExceptionHandler(ExceptionType.class)      → Handler for specific exception
@RestControllerAdvice                       → Global advice for REST controllers
```

---

### 🔄 VALIDATION FLOW DIAGRAM

```
CLIENT REQUEST (JSON)
        ↓
┌───────────────────────────────────────────────────────────────┐
│ LAYER 1: Domain Record Annotations                            │
│ @NotBlank @Size @NotEmpty @NotNull @Positive @Valid          │
│ (Constraint definitions on MeterReadings/ElectricityReading)  │
└───────────────┬─────────────────────────────────────────────────┘
                ↓
┌───────────────────────────────────────────────────────────────┐
│ LAYER 2: Controller Request Handler                           │
│ @RestController @PostMapping @RequestBody                     │
│ SmartValidator.validate(object, errors)                       │
│ → 400 Bad Request if validation fails                         │
└───────────────┬─────────────────────────────────────────────────┘
                ↓
┌───────────────────────────────────────────────────────────────┐
│ LAYER 3: Authorization Check                                  │
│ accountService.isKnownSmartMeterId(smartMeterId)             │
│ → 404 Not Found if meter not whitelisted                      │
└───────────────┬─────────────────────────────────────────────────┘
                ↓
┌───────────────────────────────────────────────────────────────┐
│ LAYER 4: Service Business Logic                              │
│ @Service MeterReadingService.storeReadings()                 │
│ Validations:                                                  │
│  - Null-safety checks                                         │
│  - Thread-safety (ConcurrentHashMap + synchronized blocks)   │
│  - Capacity limits (1000 readings/meter max)                 │
│  - FIFO eviction (remove oldest when full)                   │
└───────────────┬─────────────────────────────────────────────────┘
                ↓
┌───────────────────────────────────────────────────────────────┐
│ LAYER 5: Persistence (In-Memory Storage)                     │
│ ConcurrentMap<String, List<ElectricityReading>>              │
│ Validations:                                                  │
│  - Defensive copies (prevent external modification)          │
│  - Immutability (unmodifiableList on PricePlan)             │
│  - Data integrity checks                                     │
└───────────────┬─────────────────────────────────────────────────┘
                ↓
           200 OK
    (or 400/404 if validation failed)
```

---

### ⚠️ VALIDATION GAPS SUMMARY

| Gap | Location | Impact | Priority | Effort |
|-----|----------|--------|----------|--------|
| No GlobalExceptionHandler | Global | Generic error responses | HIGH | 1-2h |
| No domain-specific exceptions | Exception package | Cannot distinguish error types | HIGH | 1h |
| No ErrorResponse DTO | Domain | No standardized error format | HIGH | 30m |
| No @Validated on services | Service layer | Invalid params not caught | MEDIUM | 30m |
| No audit logging | Global | No validation failure trail | MEDIUM | 30m |
| No database constraints | Persistence | Validation only at app level | LOW | 2-3h |

---

### 📋 IMPLEMENTATION CHECKLIST - PHASE 1

**Recommended Implementation Order**:

```
Priority 1: Exception Infrastructure (2-3 hours)
  ☐ Create custom exception classes
    ☐ MeterNotFoundException
    ☐ InvalidMeterReadingException
    ☐ PricePlanNotFoundException
  ☐ Create GlobalExceptionHandler with @RestControllerAdvice
  ☐ Create ErrorResponse DTO record
  ☐ Update all controllers to throw exceptions instead of returning status

Priority 2: Enhanced Error Messages (1 hour)
  ☐ Capture and return constraint violations in ErrorResponse
  ☐ Include field names and failed constraints
  ☐ Add error timestamps and unique error IDs

Priority 3: Audit & Observability (1 hour)
  ☐ Add structured logging to GlobalExceptionHandler
  ☐ Log meter ID, timestamp, validation failure reason
  ☐ Create audit trail for security analysis

Priority 4: Service-Level Validation (1 hour)
  ☐ Add @Validated annotation to service classes
  ☐ Create SmartMeterValidator for authorization checks
  ☐ Update exception handler for ConstraintViolationException
```

**Estimated Total Effort**: 5-6 hours for Phase 1 exception handling

---

### 🎯 VALIDATION CLASSIFICATION SUMMARY

| Classification | Current Status | Files Affected | Spring Annotations | Code Pattern |
|---|---|---|---|---|
| **Input Data Validation** | ✅ COMPLETE | Domain records | @NotBlank, @NotEmpty, @Size, @NotNull, @Positive, @Valid | Bean Validation |
| **Request Parsing** | ✅ COMPLETE | Controllers | @RestController, @RequestBody, @PostMapping | Deserialization |
| **Business Logic Validation** | ✅ COMPLETE | Controllers, Services | Manual if() guards | if (condition) return status |
| **Authorization Validation** | ✅ COMPLETE | Controllers | None | accountService.isKnown... |
| **Thread Safety Validation** | ✅ COMPLETE | Services | @Service | synchronized, ConcurrentMap |
| **Capacity Limit Validation** | ✅ COMPLETE | Services | None | size() checks, FIFO eviction |
| **Data Integrity Validation** | ✅ COMPLETE | Domain | None | unmodifiableList, defensive copy |
| **Global Exception Handling** | ❌ MISSING | Global | @RestControllerAdvice, @ExceptionHandler | N/A |
| **Domain-Specific Exceptions** | ❌ MISSING | Exception package | Custom classes | throw new ... |
| **Standardized Error Response** | ❌ MISSING | Global | None | ErrorResponse DTO |
| **Audit Logging** | ❌ MISSING | Global | None | logger.warn/error |
| **Persistence Constraints** | ❌ MISSING | Database | None (future) | SQL CHECK, FK |

---

## 📝 Files Created

1. **VALIDATION_CLASSIFICATION.md** (12.2 KB)
   - Comprehensive layer-by-layer breakdown
   - Gap analysis and recommendations
   - Implementation roadmap
   - Detailed annotations reference

2. **VALIDATION_LAYERS_DIAGRAM.md** (22 KB)
   - Visual architecture diagrams
   - Error flow illustrations
   - Annotation hierarchy
   - Quick reference tables

3. **VALIDATION_SUMMARY_QUICK_REFERENCE.md** (This file)
   - Quick lookup for validation layers
   - Spring annotations by function
   - Implementation checklist
   - Phase 1 priorities

---

## ✅ RECOMMENDATION

**Next Step**: Implement Phase 1 exception handling infrastructure

**Rationale**:
- Improves error visibility for debugging
- Provides actionable error messages to API clients
- Enables audit trail for security analysis
- Aligns with Spring Boot best practices
- Foundation for Phase 2 (enhanced messages) and Phase 3 (service validation)

**Time Investment**: 2-3 hours for production-ready exception handling

---

**Last Updated**: 2026-06-09  
**Status**: Classification complete; ready for implementation  
**Next**: Await your approval to proceed with Phase 1 exception handling
