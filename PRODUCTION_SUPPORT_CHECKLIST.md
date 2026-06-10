# Production Support Checklist

## 1. Service availability

- [ ] Java process is running
- [ ] Port 8080 is listening
- [ ] `/readings/*` and `/price-plans/*` endpoints are reachable

## 2. Validate failing request

- [ ] Capture exact endpoint + payload
- [ ] Capture full error JSON
- [ ] Note `httpStatus`, `code`, `message`, `timestamp`

## 3. Map by code

- [ ] `BAD_REQUEST` -> validate payload/query params against API contract
- [ ] `NOT_FOUND` -> verify meter ID exists and has readings
- [ ] `INTERNAL_SERVER_ERROR` -> inspect stack trace in logs

## 4. Known current repo issue

- [ ] `functionalTest` failure is expected until `EndpointTest` package is corrected from `java.uk.tw.energy` to `uk.tw.energy`

## 5. Smoke checks

```bash
curl -X POST http://localhost:8080/readings/store -H "Content-Type: application/json" -d "{\"smartMeterId\":\"smart-meter-0\",\"electricityReadings\":[{\"time\":\"2026-06-10T05:00:00Z\",\"reading\":1.5}]}"
curl http://localhost:8080/readings/read/smart-meter-0
curl http://localhost:8080/price-plans/compare-all/smart-meter-0
curl "http://localhost:8080/price-plans/recommend/smart-meter-0?limit=2"
```
