# Production Support Checklist

**Use this during incident response to systematically resolve issues**

---

## 1. Application Not Responding (No HTTP 200 at all)

**☐ Step 1: Verify Java is running**
```bash
ps aux | grep java | grep ecomart
```
- [ ] Process found → Go to Step 2
- [ ] Process NOT found → Restart: `java -jar app.jar` or `systemctl start ecomart-api`

**☐ Step 2: Check port is listening**
```bash
netstat -tlnp | grep 8080
# or
lsof -i :8080
```
- [ ] Port open → Go to Step 3
- [ ] Port closed → Check logs: `tail -50 app.log`

**☐ Step 3: Test basic connectivity**
```bash
curl -v http://localhost:8080/price-plans 2>&1 | head -20
```
- [ ] 200 OK → Application is healthy, issue is specific request
- [ ] Connection refused → Port not listening, restart Java
- [ ] Timeout → Application hung, restart it

**☐ Resolution**: Application restarted successfully ✓

---

## 2. Validation Errors (400 Bad Request)

**☐ Step 1: Identify the error type**
```bash
# Get the full error response
curl -X POST http://localhost:8080/readings \
  -H "Content-Type: application/json" \
  -d '<your request here>' | jq .
```

**☐ Step 2: Check error code in response**
- [ ] `VALIDATION_ERROR` → Fields in `details` show what's wrong
- [ ] `CONSTRAINT_VIOLATION` → Business rule violated, check `details`
- [ ] `INVALID_METER_READING` → Data format is wrong

**☐ Step 3: Fix based on error type**
- [ ] VALIDATION_ERROR: Ensure smartMeterId is not blank and electricityReadings array has items
- [ ] CONSTRAINT_VIOLATION: Limit must be > 0, smartMeterId must not be blank
- [ ] INVALID_METER_READING: Timestamp must be number, value must be number

**☐ Step 4: Verify pattern validation**
```bash
# If error says "must contain only alphanumeric and hyphens"
echo "smart-meter-0!" | grep -E "^[a-zA-Z0-9-]+$" && echo "Valid" || echo "Invalid"
# Remove special characters, keep only a-z, A-Z, 0-9, -
```

**☐ Resolution**: Request reformatted and resent successfully ✓

---

## 3. Not Found Errors (404)

**☐ Step 1: Identify which resource is missing**
```bash
# Check error response
curl -X POST http://localhost:8080/readings \
  -H "Content-Type: application/json" \
  -d '{"smartMeterId": "unknown-meter-0", "electricityReadings": [...]}' | jq .
```

**☐ Step 2: Determine if METER_NOT_FOUND or PRICE_PLAN_NOT_FOUND**
- [ ] METER_NOT_FOUND → Store readings first
- [ ] PRICE_PLAN_NOT_FOUND → Price plan doesn't exist in system

**☐ Step 3: For METER_NOT_FOUND, store readings first**
```bash
# 1. Create readings
curl -X POST http://localhost:8080/readings \
  -H "Content-Type: application/json" \
  -d '{
    "smartMeterId": "smart-meter-0",
    "electricityReadings": [
      { "time": 1633104000000, "value": 25.5 }
    ]
  }'

# Expected: 200 OK

# 2. Now query it
curl http://localhost:8080/readings/smart-meter-0
# Expected: 200 OK with readings
```

**☐ Step 4: For PRICE_PLAN_NOT_FOUND, verify price plan exists**
```bash
# List all price plans
curl http://localhost:8080/price-plans | jq '.[] | .planId'
# Use one of the returned IDs
```

**☐ Resolution**: Meter has readings or price plan verified ✓

---

## 4. Internal Server Errors (500)

**⚠️ CRITICAL: Real bug detected, requires investigation**

**☐ Step 1: Find the error ID**
```bash
# Error response contains errorId, e.g., "ERR-Z1A2B3C4"
curl ... | jq '.errorId'
```

**☐ Step 2: Search logs for full error**
```bash
grep "ERR-Z1A2B3C4" app.log | head -5
# Look for ERROR level with stack trace
```

**☐ Step 3: Check error type**
- [ ] NullPointerException → Data corruption, restart app
- [ ] OutOfMemoryError → Increase heap: `-Xmx2g`
- [ ] Other exception → Development team investigation needed

**☐ Step 4: If data corruption suspected, restart**
```bash
# Restart application (loses in-memory data, but clears corruption)
systemctl restart ecomart-api
# or
docker restart ecomart-api

# Re-run the request
curl ...
```

**☐ Step 5: If error persists, escalate**
```bash
# Collect diagnostic info
errorId="ERR-Z1A2B3C4"
grep "$errorId" app.log > debug-$errorId.log

# Send to development:
# 1. Error ID
# 2. Request that caused it
# 3. debug-$errorId.log file
# 4. When error started
```

**☐ Resolution**: Error reproduced and logs collected for escalation ✓

---

## 5. High Error Rate (> 5% of requests failing)

**☐ Step 1: Identify error pattern**
```bash
# Count errors by type in last 100 errors
tail -100 app.log | grep -E "WARN|ERROR" | jq '.code' 2>/dev/null | sort | uniq -c

# Or filter by time
grep "2026-06-09T10:" app.log | grep -c "WARN\|ERROR"
```

**☐ Step 2: Check if it's validation errors or server errors**
- [ ] Mostly 400s (VALIDATION_ERROR) → Client sending bad data
- [ ] Mostly 500s (INTERNAL_ERROR) → Server has a bug
- [ ] Mix of both → Check logs more carefully

**☐ Step 3: For high validation errors, check client**
```bash
# Example: 90% of errors are "must not be blank"
grep "VALIDATION_ERROR" app.log | grep "must not be blank" | wc -l
# Indicate to client to add validation on their side
```

**☐ Step 4: For high server errors, restart and monitor**
```bash
# Restart application
systemctl restart ecomart-api

# Monitor error rate for next 5 minutes
watch -n 10 'tail -20 app.log | grep -E "WARN|ERROR"'
```

**☐ Resolution**: Error pattern identified and corrected ✓

---

## 6. High Memory Usage (> 80% of heap)

**☐ Step 1: Check current memory**
```bash
# Find Java process
ps aux | grep java | grep -v grep

# Get memory usage (RSS column)
# Example: ecomart-api using 900MB

# Or use jmap for heap dump
jmap -heap <pid> | grep -E "used|max"
```

**☐ Step 2: Investigate storage size**
```bash
# In-memory storage limited to 1000 readings per meter
# If many meters with full storage, memory will be high

# Check how many meters have readings
# (No direct endpoint, check application logs for storage size)
tail app.log | grep -i "storage\|memory"
```

**☐ Step 3: Options to reduce memory**
- [ ] **Increase heap size**: Restart with `-Xmx2g` instead of `-Xmx1g`
- [ ] **Clear old data**: Restart application (in-memory, data will be lost)
- [ ] **Migrate to database**: For production, use real database instead of in-memory

**☐ Step 4: Restart with increased memory**
```bash
# If using systemd service, edit /etc/systemd/system/ecomart.service
# Change ExecStart line to include larger -Xmx

# Or start manually
java -Xmx2g -jar app.jar

# Verify memory usage
ps aux | grep java | grep -v grep
```

**☐ Resolution**: Memory usage monitored and reduced ✓

---

## 7. Slow Response Times (> 500ms p95)

**☐ Step 1: Check if it's sustained or temporary**
```bash
# Measure response time
time curl http://localhost:8080/readings/smart-meter-0
# Look at "real" time (should be < 100ms normally)

# Run 10 times and check if consistent
for i in {1..10}; do 
  time curl -s http://localhost:8080/readings/smart-meter-0 > /dev/null
done
```

**☐ Step 2: If storage is full (1000 readings/meter), that could be slow**
```bash
# Try with different meter
curl http://localhost:8080/readings/smart-meter-1
# If faster, the first meter has many readings
```

**☐ Step 3: Check if application is CPU bound**
```bash
# Monitor CPU during request
top -p <pid> -n 10

# If CPU is 100%, it's slow processing (normal for large storage)
# If CPU is low but slow response, it might be I/O wait
```

**☐ Step 4: If consistently slow**
- [ ] Restart application to clear in-memory storage
- [ ] Monitor if performance improves

**☐ Resolution**: Performance baseline established and within acceptable range ✓

---

## 8. Failed Deployment (Build or Tests Failing)

**☐ Step 1: Verify build environment**
```bash
java -version
# Expected: OpenJDK 11 or later

./gradlew --version
# Expected: Shows Gradle version
```

**☐ Step 2: Clean build**
```bash
./gradlew clean build

# Look for error message at end
# If still failing, run tests individually
```

**☐ Step 3: Run tests step by step**
```bash
# Unit tests
./gradlew test

# If failed, find the test name in output
# Re-run specific test with more details
./gradlew test --info

# Functional tests
./gradlew functionalTest
```

**☐ Step 4: Common build failures**
- [ ] **Java version too old**: Upgrade to Java 11+
- [ ] **Port 8080 in use**: Change port: `--server.port=8081`
- [ ] **Disk space issue**: Check: `df -h`
- [ ] **Permission denied**: Check file permissions: `ls -la build/`

**☐ Resolution**: Build completed successfully ✓

---

## Quick Command Reference

```bash
# Start application
java -jar syssupportengineer-ecomart-java-*.jar

# Test connectivity
curl http://localhost:8080/price-plans

# Store readings
curl -X POST http://localhost:8080/readings \
  -H "Content-Type: application/json" \
  -d '{"smartMeterId": "sm-0", "electricityReadings": [{"time": 1633104000000, "value": 25.5}]}'

# Get readings for meter
curl http://localhost:8080/readings/sm-0

# Compare price plans
curl "http://localhost:8080/price-plans/compare-for?smartMeterId=sm-0&limit=2"

# View logs
tail -50 app.log

# Search for error
grep "ERR-X9Y8Z7W6" app.log

# Count validation failures
grep "VALIDATION_ERROR" app.log | wc -l

# Monitor application
watch -n 5 'tail -20 app.log'

# Restart application
systemctl restart ecomart-api

# Check memory
ps aux | grep java | grep -v grep
```

---

## Escalation Decision Tree

```
Is issue resolved?
├─ YES → Document resolution and close ticket ✓
└─ NO → Continue

Is it a known issue in this checklist?
├─ YES → Follow the steps above
└─ NO → Continue

Can you reproduce the issue?
├─ YES → Capture: request, response, errorId, logs
└─ NO → Document what you've tried, escalate

Should you restart the application?
├─ YES (500 errors, memory high) → Restart and monitor
└─ NO (4xx errors, client issue) → Fix on client side

Escalate to development with:
  1. Error ID (ERR-XXXXXXXX)
  2. Request sent
  3. Response received
  4. Relevant logs (grep errorId)
  5. Steps to reproduce
  6. Time of incident
```

---

## Contacts

| Role | Responsibility | Contact |
|------|-----------------|---------|
| **Support Engineer** | Use this checklist | Internal |
| **DevOps** | Application deployment, restarts | DevOps Team |
| **Development** | Bug fixes, code issues | Development Team |
| **Architecture** | System design, database migration | Architecture Team |

---

**Last Updated**: 2026-06-09  
**Version**: 9.1/10  
**Status**: Production Ready ✅
