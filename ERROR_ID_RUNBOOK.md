# EcoMart Energy API - Error ID Runbook

**Quick Reference for Production Support**  
**How to use**: When you see an error, find the `errorId` in the response and look it up below.

---

## Error ID Format

Every error includes a unique ID:
```json
{
  "errorId": "ERR-A1B2C3D4",
  "code": "VALIDATION_ERROR",
  "httpStatus": 400,
  "message": "Request validation failed",
  "details": { ... }
}
```

---

## Quick Error Mapping

| HTTP | Code | Cause | Runbook |
|------|------|-------|---------|
| 400 | VALIDATION_ERROR | Request body invalid | [#1](#1-validation_error-400) |
| 400 | CONSTRAINT_VIOLATION | Service validation failed | [#2](#2-constraint_violation-400) |
| 400 | INVALID_METER_READING | Reading data malformed | [#3](#3-invalid_meter_reading-400) |
| 404 | METER_NOT_FOUND | Meter doesn't exist | [#4](#4-meter_not_found-404) |
| 404 | PRICE_PLAN_NOT_FOUND | Price plan doesn't exist | [#5](#5-price_plan_not_found-404) |
| 500 | INTERNAL_ERROR | Unexpected exception | [#6](#6-internal_error-500) |

---

## Detailed Runbooks

### #1. VALIDATION_ERROR (400)

**What happened**: Request body failed validation at API entry point

**Example Response**:
```json
{
  "httpStatus": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": {
    "smartMeterId": "must not be blank",
    "electricityReadings": "must not be empty"
  },
  "errorId": "ERR-A1B2C3D4",
  "timestamp": "2026-06-09T10:30:45.123+08:00"
}
```

**What to check**:
1. Each field in `details` shows validation rule that failed
2. Possible validation rules:
   - `smartMeterId: must not be blank` → Send non-empty meter ID
   - `smartMeterId: must contain only alphanumeric characters and hyphens` → Remove special chars (!, @, #, $, %, etc.)
   - `electricityReadings: must not be empty` → Include at least 1 reading
   - `electricityReadings[0].time: must not be null` → Include timestamp (milliseconds)
   - `electricityReadings[0].value: must not be null` → Include reading value

**Fix**:
```bash
# ❌ WRONG
curl -X POST http://localhost:8080/readings \
  -H "Content-Type: application/json" \
  -d '{
    "smartMeterId": "smart-meter-0!",
    "electricityReadings": []
  }'

# ✅ CORRECT
curl -X POST http://localhost:8080/readings \
  -H "Content-Type: application/json" \
  -d '{
    "smartMeterId": "smart-meter-0",
    "electricityReadings": [
      { "time": 1633104000000, "value": 25.5 }
    ]
  }'
```

**How to search logs**:
```bash
grep "ERR-A1B2C3D4" app.log
# Output: WARN [controller] Validation failed [errorId=ERR-A1B2C3D4] field_count=2 fields=[...]
```

---

### #2. CONSTRAINT_VIOLATION (400)

**What happened**: Service parameter validation failed (inside application logic)

**Example Response**:
```json
{
  "httpStatus": 400,
  "code": "CONSTRAINT_VIOLATION",
  "message": "Constraint violation: limit must be greater than 0",
  "details": {
    "limit": "must be greater than 0"
  },
  "errorId": "ERR-X9Y8Z7W6",
  "timestamp": "2026-06-09T10:30:45.123+08:00"
}
```

**What to check**:
1. Look at the `details` field for which constraint was violated
2. Common constraint violations:
   - `limit: must be greater than 0` → Use positive integer for limit
   - `smartMeterId: must not be blank` → Meter ID cannot be empty in query

**Fix**:
```bash
# ❌ WRONG - limit is 0
curl "http://localhost:8080/price-plans/compare-for?smartMeterId=smart-meter-0&limit=0"

# ✅ CORRECT - limit is positive
curl "http://localhost:8080/price-plans/compare-for?smartMeterId=smart-meter-0&limit=2"
```

**How to search logs**:
```bash
grep "ERR-X9Y8Z7W6" app.log
# Output: WARN [service] Constraint violation [errorId=ERR-X9Y8Z7W6] violation_count=1 violations=[limit: must be greater than 0]
```

---

### #3. INVALID_METER_READING (400)

**What happened**: Meter reading data is malformed (unexpected format)

**Example Response**:
```json
{
  "httpStatus": 400,
  "code": "INVALID_METER_READING",
  "message": "Invalid meter reading format",
  "details": "Timestamp and value must be valid",
  "errorId": "ERR-M1N2O3P4",
  "timestamp": "2026-06-09T10:30:45.123+08:00"
}
```

**What to check**:
1. Timestamp must be valid milliseconds (number, > 0)
2. Value must be valid number (decimal, positive or zero)
3. Each reading must have both `time` and `value` fields

**Fix**:
```bash
# ❌ WRONG - value is string
curl -X POST http://localhost:8080/readings \
  -d '{ "smartMeterId": "sm-0", "electricityReadings": [{"time": 1633104000000, "value": "25.5"}] }'

# ✅ CORRECT - value is number
curl -X POST http://localhost:8080/readings \
  -d '{ "smartMeterId": "sm-0", "electricityReadings": [{"time": 1633104000000, "value": 25.5}] }'
```

---

### #4. METER_NOT_FOUND (404)

**What happened**: Meter ID is unknown or doesn't have any readings yet

**Example Response**:
```json
{
  "httpStatus": 404,
  "code": "METER_NOT_FOUND",
  "message": "Smart meter not found: unknown-meter-id",
  "details": "Meter 'unknown-meter-id' has no readings",
  "errorId": "ERR-D5E6F7G8",
  "timestamp": "2026-06-09T10:30:45.123+08:00"
}
```

**What to check**:
1. Meter ID might not be registered (not in the system's known meters)
2. OR meter is registered but has no readings stored yet
3. OR meter ID is misspelled

**Fix**:
```bash
# 1. Store readings for meter first
curl -X POST http://localhost:8080/readings \
  -H "Content-Type: application/json" \
  -d '{
    "smartMeterId": "smart-meter-0",
    "electricityReadings": [
      { "time": 1633104000000, "value": 25.5 }
    ]
  }'
# Expected: 200 OK

# 2. Now query the meter
curl http://localhost:8080/readings/smart-meter-0
# Expected: 200 OK with readings
```

**How to search logs**:
```bash
grep "ERR-D5E6F7G8" app.log
# Output: INFO [service] Meter not found: Smart meter not found: unknown-meter-id
```

---

### #5. PRICE_PLAN_NOT_FOUND (404)

**What happened**: Price plan ID is unknown or doesn't exist in the system

**Example Response**:
```json
{
  "httpStatus": 404,
  "code": "PRICE_PLAN_NOT_FOUND",
  "message": "Price plan not found",
  "details": "Price plan 'unknown-plan-id' does not exist",
  "errorId": "ERR-H9I0J1K2",
  "timestamp": "2026-06-09T10:30:45.123+08:00"
}
```

**What to check**:
1. Price plan ID might be misspelled
2. Price plan might have been deleted
3. Price plan might not be in the system

**Fix**:
```bash
# 1. List all available price plans
curl http://localhost:8080/price-plans | jq .

# 2. Use one of the returned price plan IDs in your request
# Example: { "planId": "price-plan-0", "planName": "Esme Rate 2" }
```

**How to search logs**:
```bash
grep "ERR-H9I0J1K2" app.log
# Output: INFO [service] Price plan not found: Price plan 'unknown-plan-id' does not exist
```

---

### #6. INTERNAL_ERROR (500)

**What happened**: Unexpected exception occurred (not a validation error)

**Example Response**:
```json
{
  "httpStatus": 500,
  "code": "INTERNAL_ERROR",
  "message": "Internal server error",
  "details": "An unexpected error occurred",
  "errorId": "ERR-Z1A2B3C4",
  "timestamp": "2026-06-09T10:30:45.123+08:00"
}
```

**What to check**:
1. This is a real bug - look in logs for full details
2. Use the errorId to find the stack trace
3. Check if application is still responding to other requests

**Fix**:
```bash
# 1. Search logs for the errorId to see full error
grep "ERR-Z1A2B3C4" app.log

# Expected output:
# ERROR [...] Unexpected exception [errorId=ERR-Z1A2B3C4] type=NullPointerException message=...
# [stack trace...]

# 2. If many 500 errors, restart application
systemctl restart ecomart-api
# or
docker restart ecomart-api

# 3. If error persists after restart, escalate to development team with:
# - Error ID (ERR-Z1A2B3C4)
# - Request details (what you were trying to do)
# - Logs from the time of error
```

**Common causes**:
- ❌ Storage corrupted → Restart (in-memory, data lost)
- ❌ Out of memory → Increase heap: `-Xmx2g`
- ❌ Bug in application code → Requires developer investigation

---

## Log Query Patterns

### Find errors for specific meter
```bash
grep "smart-meter-0" app.log | grep -E "WARN|ERROR"
```

### Find all validation errors in last hour
```bash
grep "WARN.*Validation failed" app.log | tail -30
```

### Find specific error by ID
```bash
grep "ERR-X9Y8Z7W6" app.log
```

### Count error types
```bash
grep "code=" app.log | jq .code | sort | uniq -c
```

### Find errors for specific time range
```bash
grep "2026-06-09T10:" app.log | grep -E "WARN|ERROR"
```

---

## Decision Tree

```
Got an error response? Follow this flow:

1. Look at the "errorId" field (e.g., ERR-A1B2C3D4)

2. Check the HTTP status code:
   
   400 Bad Request?
   └─→ "VALIDATION_ERROR" → Check "details" field, fix request format
   └─→ "CONSTRAINT_VIOLATION" → Check "details" field, fix parameter values
   └─→ "INVALID_METER_READING" → Fix timestamp/value format
   
   404 Not Found?
   └─→ "METER_NOT_FOUND" → Store readings first, check meter ID
   └─→ "PRICE_PLAN_NOT_FOUND" → Verify price plan ID exists
   
   500 Server Error?
   └─→ "INTERNAL_ERROR" → Search logs with errorId, check stack trace, restart if needed

3. If you can't fix it, provide:
   - errorId (for log correlation)
   - Request you sent
   - Response received
   - Logs around that time
   
→ Send to development team for investigation
```

---

## Escalation Paths

### Level 1: Self-Service (Check These First)
- ✓ Is request format correct? (valid JSON, all required fields)
- ✓ Is meter ID in correct format? (alphanumeric + hyphens only)
- ✓ Does meter have readings? (store first with POST /readings)
- ✓ Is application responding? (curl to /price-plans)

### Level 2: DevOps (If Level 1 doesn't work)
- ✓ Application logs showing errors?
- ✓ Is application running? (check systemctl status)
- ✓ Is it a 500 error? (restart application)
- ✓ Memory exhausted? (increase heap or restart)

### Level 3: Development (For persistent issues)
- ✓ Collect: errorId, request, full response, logs
- ✓ Provide: time of error, what you were doing, steps to reproduce
- ✓ Attach: `grep "ERR-XXXXX" app.log` output with full stack trace

---

## Performance Baseline (for alerting)

| Metric | Normal | Warning | Alert |
|--------|--------|---------|-------|
| Response time p95 | < 100ms | 100-500ms | > 500ms |
| Error rate | < 1% | 1-5% | > 5% |
| Validation failures/min | < 10 | 10-50 | > 50 |
| Memory usage | < 60% | 60-80% | > 80% |

---

**Last Updated**: 2026-06-09  
**Version**: 9.1/10 (Production Ready)  
**For questions, see DEPLOYMENT_GUIDE.md**
