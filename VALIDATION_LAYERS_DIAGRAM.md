# Validation Layers - Visual Architecture

## Request Flow with Validation Points

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT REQUEST                              │
│                    POST /readings/store                             │
│                 with JSON MeterReadings payload                     │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│            LAYER 1: DESERIALIZATION & FORMAT                        │
│                  (Spring Framework)                                 │
├─────────────────────────────────────────────────────────────────────┤
│  Input:  Raw JSON                                                   │
│  Action: Parse JSON → MeterReadings record                          │
│  Output: MeterReadings object (may be partially invalid)            │
│                                                                     │
│  Annotations: @RequestBody, @RestController                        │
│  Error: 400 if JSON malformed                                       │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│        LAYER 2: NULL & TYPE VALIDATION                              │
│              (Controller Boundary)                                  │
├─────────────────────────────────────────────────────────────────────┤
│  Validation:                                                        │
│    if (meterReadings == null) {                                     │
│      return 400 Bad Request                                         │
│    }                                                                │
│                                                                     │
│  Annotations: @RestController, @PostMapping                        │
│  Error: 400 if null                                                │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│      LAYER 3: DOMAIN CONSTRAINTS (Bean Validation)                 │
│              (Domain Record + Validator)                            │
├─────────────────────────────────────────────────────────────────────┤
│  MeterReadings Constraints:                                         │
│    ✓ @NotBlank smartMeterId                                        │
│    ✓ @Size(max=64) smartMeterId                                    │
│    ✓ @NotEmpty electricityReadings                                 │
│    ✓ @Size(max=1000) electricityReadings                           │
│    ✓ @Valid electricityReadings[*] (cascade)                       │
│                                                                     │
│  ElectricityReading Constraints:                                    │
│    ✓ @NotNull time                                                 │
│    ✓ @NotNull reading                                              │
│    ✓ @Positive reading (kW > 0)                                    │
│                                                                     │
│  Validator: SmartValidator.validate(meterReadings, errors)         │
│  Error: 400 if any constraint violated                             │
│  Annotations: @NotBlank, @NotEmpty, @Size, @NotNull, @Positive    │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│      LAYER 4: AUTHORIZATION (Business Logic)                       │
│            (AccountService + Controller)                           │
├─────────────────────────────────────────────────────────────────────┤
│  Validation:                                                        │
│    if (!accountService.isKnownSmartMeterId(smartMeterId)) {        │
│      return 404 Not Found                                          │
│    }                                                                │
│                                                                     │
│  Purpose: Only registered meters can write data                    │
│  Error: 404 if meter not whitelisted                               │
│  Context: Meter whitelist stored in AccountService                 │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│      LAYER 5: SERVICE-LEVEL VALIDATION                              │
│          (MeterReadingService.storeReadings)                        │
├─────────────────────────────────────────────────────────────────────┤
│  Validations:                                                       │
│    ✓ Null-safety: if (smartMeterId == null)                        │
│    ✓ Thread-safety: synchronized (existingReadings) blocks         │
│    ✓ Capacity: if (size > MAX_READINGS_PER_METER)                  │
│    ✓ FIFO Eviction: remove oldest readings when overflow           │
│                                                                     │
│  Annotations: @Service                                              │
│  Error: None thrown; silently evicts old data                      │
│  Context: ConcurrentHashMap + synchronized lists                    │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│      LAYER 6: PERSISTENCE (In-Memory Storage)                      │
│         (ConcurrentMap with thread-safe containers)               │
├─────────────────────────────────────────────────────────────────────┤
│  Data Structure:                                                    │
│    ConcurrentMap<String, List<ElectricityReading>>                 │
│      ↓                                                              │
│    Collections.synchronizedList(new ArrayList<>())                 │
│                                                                     │
│  Validations:                                                       │
│    ✓ Defensive copy on read: new ArrayList<>(readings)            │
│    ✓ Immutability on PricePlan: unmodifiableList()                │
│    ✓ Bounded storage: 1000 readings/meter                          │
│                                                                     │
│  Error: None; constraints enforced silently                        │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    SUCCESS RESPONSE                                 │
│                       200 OK                                        │
│                  Readings stored                                    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Error Response Flow (When Validation Fails)

```
ANY VALIDATION FAILURE
        │
        ▼
┌─────────────────────────────────────┐
│  Where was it caught?               │
│  1. Null check           → 400       │
│  2. Bean Validation      → 400       │
│  3. Authorization        → 404       │
│  4. Service-level        → 500?      │
│  5. Persistence          → Silent    │
└────────────────┬────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────┐
│  PROBLEM: No Centralized Handler    │
│  ❌ No GlobalExceptionHandler        │
│  ❌ No ErrorResponse DTO             │
│  ❌ No Audit Logging                 │
│  ❌ No Detailed Error Messages       │
│                                     │
│  Current: Generic 400 or 404        │
│  Needed: Detailed error object      │
└─────────────────────────────────────┘
```

---

## Validation Annotation Hierarchy

```
                    ┌──────────────────────┐
                    │   Request arrives    │
                    │   (JSON payload)     │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │ @RestController     │
                    │ @PostMapping        │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │ @RequestBody        │
                    │ (Deserialization)   │
                    └──────────┬───────────┘
                               │
        ┌──────────────────────┴──────────────────────┐
        │                                             │
        ▼                                             ▼
┌──────────────────────┐              ┌──────────────────────────┐
│  MeterReadings       │              │  ElectricityReading      │
│  @NotBlank           │              │  @NotNull                │
│  @Size(max=64)       │              │  @NotNull @Positive      │
│  @NotEmpty           │              │                          │
│  @Size(max=1000)     │              │                          │
│  @Valid              │              │                          │
└──────────┬───────────┘              └───────────┬──────────────┘
           │                                      │
           │      Cascading Validation            │
           └──────────────┬──────────────────────┘
                          │
                ┌─────────▼──────────┐
                │ SmartValidator     │
                │ .validate()        │
                └─────────┬──────────┘
                          │
                ┌─────────▼──────────────────┐
                │ Errors object populated    │
                │ if (errors.hasErrors())    │
                └─────────┬──────────────────┘
                          │
        ┌─────────────────┴─────────────────┐
        │                                   │
        ▼                          ▼
    ✓ PASS                    ✗ FAIL
    │                         │
    ▼                         ▼
Continue to         HTTP 400 Bad Request
Authorization       (❌ No details)
```

---

## Spring Boot Annotations by Function

### REQUEST HANDLING

```
@RestController
    │
    ├── @RequestMapping("/base")
    │   │
    │   ├── @PostMapping("/store")
    │   │   └── @RequestBody MeterReadings
    │   │
    │   └── @GetMapping("/read/{smartMeterId}")
    │       └── @PathVariable String smartMeterId
    │
    └── @RequestParam(value="limit", required=false) Integer limit
```

### VALIDATION

```
jakarta.validation (annotations on domain objects)
    │
    ├── @NotBlank
    ├── @NotEmpty
    ├── @NotNull
    ├── @Positive
    ├── @Size(max=N)
    └── @Valid (cascade to nested objects)

org.springframework.validation (SmartValidator)
    │
    └── validator.validate(object, errors)
```

### COMPONENT REGISTRATION

```
@Service
    │
    └── MeterReadingService
        PricePlanService
        AccountService

@Configuration
    │
    └── Bean factories
```

### EXCEPTION HANDLING (MISSING)

```
@RestControllerAdvice (NEEDS TO BE ADDED)
    │
    └── @ExceptionHandler(MethodArgumentNotValidException.class)
        @ExceptionHandler(MeterNotFoundException.class)
        @ExceptionHandler(Exception.class)
```

---

## Validation Coverage Map

```
LAYER              STATUS    COVERAGE              GAPS
─────────────────────────────────────────────────────────────
Domain             ✅ COMPLETE   @NotBlank, @NotNull, @Positive
                                @Size, @NotEmpty, @Valid

Controller         ✅ COMPLETE   @RequestBody, SmartValidator
Request Boundary   ✅ COMPLETE   Null-checks, authorization

Service            ✅ COMPLETE   Null-safety, thread-safety
Business Logic     ✅ COMPLETE   Capacity limits, FIFO eviction

Persistence        ⚠️ PARTIAL    Defensive copies, immutability
In-Memory Storage  ⚠️ PARTIAL    ❌ No database constraints

Global             ❌ MISSING    ❌ No @RestControllerAdvice
Exception Handler  ❌ MISSING    ❌ No domain exceptions
Error Response     ❌ MISSING    ❌ No ErrorResponse DTO
```

---

## Authentication vs Authorization vs Validation

```
┌─────────────────────────────────────────────────────────────┐
│            REQUEST PROCESSING PIPELINE                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. AUTHENTICATION (NOT IMPLEMENTED)                       │
│     ├─ Who are you?                                        │
│     ├─ Upstream API Gateway handles this                  │
│     └─ This app assumes pre-authenticated requests        │
│                                                             │
│  2. AUTHORIZATION (PARTIALLY IMPLEMENTED)                  │
│     ├─ Are you allowed to access this meter?              │
│     ├─ Check: isKnownSmartMeterId(smartMeterId)           │
│     └─ Return 404 if not whitelisted                      │
│                                                             │
│  3. VALIDATION (FULLY IMPLEMENTED)                        │
│     ├─ Is your request format valid?                      │
│     ├─ Bean Validation: @NotBlank, @Positive, @Size      │
│     ├─ Business Logic: limit > 0                         │
│     └─ Return 400 if invalid                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Missing Exception Handling Layer

### CURRENT (Problematic)

```
Layer 2 → Layer 3 → Layer 4 → Service → Persistence
  │         │         │          │           │
  └─400     └─400     └─404      └─???      └─Silent
      │         │         │          │
      └─────────┴─────────┴──────────┴──→ Client Response
                                           (Generic status only)
```

### RECOMMENDED (With GlobalExceptionHandler)

```
Layer 2 → Layer 3 → Layer 4 → Service → Persistence
  │         │         │          │           │
  └─Exception─────────────────────────────────┘
      │
      ▼
@RestControllerAdvice
│
├─→ @ExceptionHandler(MethodArgumentNotValidException.class)
│   └─→ ErrorResponse { code: "VALIDATION_ERROR", messages: [...], timestamp: ... }
│
├─→ @ExceptionHandler(MeterNotFoundException.class)
│   └─→ ErrorResponse { code: "METER_NOT_FOUND", messages: [...], timestamp: ... }
│
└─→ @ExceptionHandler(Exception.class)
    └─→ ErrorResponse { code: "INTERNAL_ERROR", messages: [...], timestamp: ... }
        │
        └─→ Client Response (Detailed, actionable)
```

---

## Key Takeaways

✅ **What's Working**:
- Domain-level validation (annotations on records)
- Controller-level request validation
- Service-level business logic validation
- Thread-safety and concurrency control

❌ **What's Missing**:
- Centralized exception handling (GlobalExceptionHandler)
- Domain-specific exceptions
- Standardized error response format
- Audit logging on validation failures
- Detailed error messages to clients

🔧 **Priority Fixes**:
1. Create @RestControllerAdvice (1-2 hours)
2. Create custom exceptions (1 hour)
3. Add ErrorResponse DTO (30 minutes)
4. Add audit logging (30 minutes)

---

**Last Updated**: 2026-06-09  
**Status**: Ready for Phase 1 exception handling implementation
