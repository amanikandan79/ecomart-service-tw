# Comprehensive Validation Classification

## Executive Summary

This document classifies all validation mechanisms across the Spring Boot application, organized by layer, validation type, and Spring Boot annotations used. The current implementation covers **input data validation** across all layers but lacks **domain-specific exceptions** and **centralized global exception handling**.

---

## 1. DOMAIN LAYER VALIDATION (Input Data Constraints)

### 1.1 Annotation-Based Constraints (Jakarta Bean Validation)

#### File: src/main/java/uk/tw/energy/domain/MeterReadings.java

| Field | Constraints | Type | Severity |
|-------|-------------|------|----------|
| smartMeterId | @NotBlank | Input Data | **MUST** |
| smartMeterId | @Size(max=64) | Input Data | **MUST** |
| electricityReadings | @NotEmpty | Input Data | **MUST** |
| electricityReadings | @Size(max=1000) | Input Data | **MUST** |
| electricityReadings[*] | @Valid | Nested Cascade | **MUST** |

**Error Response**: HTTP 400 Bad Request with constraint violation details

---

#### File: src/main/java/uk/tw/energy/domain/ElectricityReading.java

| Field | Constraints | Type | Severity |
|-------|-------------|------|----------|
| time | @NotNull | Input Data | **MUST** |
| reading | @NotNull | Input Data | **MUST** |
| reading | @Positive | Business Logic | **MUST** |

**Error Response**: HTTP 400 Bad Request if reading <= 0

---

## 2. CONTROLLER LAYER VALIDATION (Request Boundary)

### 2.1 MeterReadingController Validation Flow

**Endpoint**: POST /readings/store

Validation Steps:
1. Null-check (safety net) -> 400
2. Bean Validation (SmartValidator) -> 400
3. Authorization Check (isKnownSmartMeterId) -> 404
4. Delegate to service -> 200 OK

**Spring Boot Annotations Used**:
- @RestController — Marks class as REST endpoint handler
- @PostMapping("/store") — Maps HTTP POST to method
- @RequestBody — Deserializes JSON to MeterReadings record
- @RequestMapping("/readings") — Base path for all endpoints

---

### 2.2 PricePlanComparatorController Validation

**Endpoint**: GET /price-plans/recommend/{smartMeterId}

| Validation Type | Code | HTTP Status |
|-----------------|------|------------|
| Business Logic | limit <= 0 | 400 Bad Request |
| Resource Check | consumptionsForPricePlans.isPresent() | 404 Not Found |

**Spring Boot Annotations Used**:
- @GetMapping — Maps HTTP GET
- @PathVariable — Extracts URL path parameter
- @RequestParam — Extracts query parameter (?limit=N)

---

## 3. SERVICE LAYER VALIDATION (Business Logic)

### 3.1 Defensive Copies & Thread Safety (MeterReadingService)

| Validation Type | Code Pattern | Purpose |
|-----------------|--------------|---------|
| Null-Safety | if (readings == null) | Prevent NPE |
| Defensive Copy | new ArrayList(readings) | Prevent external modification |
| Thread-Safety | synchronized (existingReadings) | Prevent race conditions |
| Capacity Check | overflow > 0 | Enforce 1000-reading limit |

**Spring Boot Annotations**: @Service (component registration)

---

### 3.2 Business Logic Validation (PricePlanService)

| Validation Type | Condition | Response |
|-----------------|-----------|----------|
| Edge Case | timeElapsed == 0 | Return 0 |
| Edge Case | readings.isEmpty() | Return 0 |
| Edge Case | readings.size() < 2 | Return 0 |
| Precision | setScale(6, HALF_UP) | BigDecimal energy |
| Precision | setScale(2, HALF_UP) | BigDecimal currency |

**Spring Boot Annotations**: @Service (component registration)

---

### 3.3 Authorization Validation (AccountService)

| Validation Type | Code | Purpose |
|-----------------|------|---------|
| Authorization | containsKey() | Verify meter is registered |

---

## 4. EXCEPTION HANDLING (MISSING - Priority to Add)

### Current State: No Global Exception Handler

**Problem**: Validation errors not caught centrally; each controller handles manually.

**Missing Components**:
- ? GlobalExceptionHandler with @RestControllerAdvice
- ? Domain-specific exceptions (MeterNotFoundException, InvalidMeterReadingException)
- ? Standardized error response format (ErrorResponse DTO)
- ? Audit logging on validation failures
- ? Detailed constraint violations in error responses

---

### Recommended Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Handle validation errors
    }
    
    @ExceptionHandler(MeterNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMeterNotFound(MeterNotFoundException ex) {
        // Handle meter not found
    }
}
```

---

## 5. IMPORTANT SPRING BOOT ANNOTATIONS

### Dependency Injection & Component Registration

| Annotation | Usage | Purpose |
|-----------|-------|---------|
| @RestController | Class-level | REST endpoint handler |
| @Service | Class-level | Business logic bean |
| @Component | Class-level | Generic Spring component |
| @Configuration | Class-level | Bean factory |
| @RestControllerAdvice | Class-level | Global exception handler |

### HTTP Mapping & Routing

| Annotation | Usage | Purpose |
|-----------|-------|---------|
| @RequestMapping("/path") | Class-level | Base URI path |
| @PostMapping("/endpoint") | Method-level | HTTP POST mapping |
| @GetMapping("/endpoint") | Method-level | HTTP GET mapping |
| @PathVariable | Parameter | Extract URL parameter |
| @RequestParam | Parameter | Extract query parameter |
| @RequestBody | Parameter | Deserialize JSON |

### Validation & Constraints

| Annotation | Source | Purpose |
|-----------|--------|---------|
| @NotBlank | jakarta.validation | String not null/empty |
| @NotEmpty | jakarta.validation | Collection not empty |
| @NotNull | jakarta.validation | Object not null |
| @Positive | jakarta.validation | Number > 0 |
| @Size(max=N) | jakarta.validation | Max length/size |
| @Valid | jakarta.validation | Cascade validation |

### Exception Handling

| Annotation | Usage | Purpose |
|-----------|-------|---------|
| @ExceptionHandler(ExceptionType.class) | Method-level | Map exception to handler |
| @RestControllerAdvice | Class-level | Centralized error handling |

---

## 6. VALIDATION COVERAGE MATRIX

| Layer | Validation Type | Current Status | HTTP Response |
|-------|-----------------|----------------|----|
| Domain | Input Constraints | ? Complete | N/A |
| Domain | Data Integrity | ? Complete | N/A |
| Controller | Request Parsing | ? Complete | 400 |
| Controller | Bean Validation | ? Complete | 400 |
| Controller | Business Logic | ? Complete | 400/404 |
| Service | Null Safety | ? Complete | N/A |
| Service | Business Logic | ? Complete | N/A |
| Service | Thread Safety | ? Complete | N/A |
| Service | Capacity Limits | ? Complete | N/A |
| Persistence | Data Constraints | ? Missing | N/A |
| Global | Exception Handling | ? Missing | N/A |
| Domain | Custom Exceptions | ? Missing | N/A |

---

## 7. IMPLEMENTATION GAPS & RECOMMENDATIONS

### Gap 1: No Global Exception Handler ??

**Impact**: Validation errors return generic 400 with no details

**Fix**: Create @RestControllerAdvice with @ExceptionHandler methods
**Effort**: 1-2 hours
**Priority**: HIGH

---

### Gap 2: No Domain-Specific Exceptions ??

**Impact**: Cannot distinguish between validation failures (bad request) and business logic failures (not found)

**Fix**: Create custom exception classes
- MeterNotFoundException
- InvalidMeterReadingException  
- PricePlanNotFoundException

**Effort**: 1 hour
**Priority**: HIGH

---

### Gap 3: No Detailed Error Responses ??

**Impact**: Client cannot determine which field failed validation

**Fix**: Return ErrorResponse DTO with field names and constraint details
**Effort**: 30 minutes
**Priority**: MEDIUM

---

### Gap 4: No @Validated on Service Methods ??

**Impact**: Services accept invalid parameters without validation

**Fix**: Add @Validated annotation to service classes
**Effort**: 30 minutes
**Priority**: MEDIUM

---

### Gap 5: No Audit Logging ??

**Impact**: No trail of validation failures for debugging/security

**Fix**: Add structured logging to exception handlers
**Effort**: 30 minutes
**Priority**: MEDIUM

---

### Gap 6: No Persistence Layer Constraints ??

**Impact**: When migrating to database, validation only at application level

**Fix**: Add CHECK constraints, FOREIGN KEY constraints in database schema
**Effort**: 2-3 hours (Phase 2)
**Priority**: LOW

---

## 8. IMPLEMENTATION ROADMAP

### Phase 1: Exception Handling Infrastructure (2-3 hours)

1. Create custom exceptions (1 hour)
   - MeterNotFoundException
   - InvalidMeterReadingException
   - PricePlanNotFoundException

2. Create GlobalExceptionHandler (45 minutes)
   - @RestControllerAdvice with handlers
   - Consistent error response format

3. Create ErrorResponse DTO (15 minutes)
   - Include error code, messages, timestamp

---

### Phase 2: Enhanced Error Messages (1 hour)

1. Update error responses (30 min)
   - Include field names and constraints

2. Add audit logging (30 min)
   - Log all validation failures

---

### Phase 3: Service-Layer Validation (1 hour)

1. Add @Validated to services (15 min)

2. Create custom validators (30 min)
   - SmartMeterValidator
   - PricePlanValidator

3. Update exception handlers (15 min)

---

## SUMMARY

**Current State**:
- ? Domain-level input validation (annotations)
- ? Controller-level request validation
- ? Service-level business logic validation
- ? Global exception handling infrastructure
- ? Domain-specific exceptions
- ? Standardized error response format

**Next Steps**:
1. Implement GlobalExceptionHandler (Priority 1)
2. Create domain-specific exceptions (Priority 1)
3. Standardize error response format (Priority 2)
4. Add @Validated to services (Priority 2)
5. Add audit logging (Priority 3)

**Status**: Ready for Phase 1 hardening; can be completed in 2-3 hours
