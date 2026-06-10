# API Reference

Base URL: `http://localhost:8080`

## 1. POST /readings/store

Stores readings for a known smart meter.

### Request

```http
POST /readings/store
Content-Type: application/json
```

```json
{
  "smartMeterId": "smart-meter-0",
  "electricityReadings": [
    { "time": "2026-06-10T05:00:00Z", "reading": 1.23 },
    { "time": "2026-06-10T05:30:00Z", "reading": 1.45 }
  ]
}
```

### Responses

- `200 OK` - stored
- `400 BAD_REQUEST` - payload validation failed
- `404 NOT_FOUND` - unknown meter ID

---

## 2. GET /readings/read/{smartMeterId}

Returns readings for a meter.

### Request

```http
GET /readings/read/smart-meter-0
```

### Responses

- `200 OK` - JSON array of readings
- `404 NOT_FOUND` - meter unknown or no readings found

---

## 3. GET /price-plans/compare-all/{smartMeterId}

Calculates cost across all plans.

### Request

```http
GET /price-plans/compare-all/smart-meter-0
```

### Success response

```json
{
  "pricePlanId": "price-plan-0",
  "pricePlanComparisons": {
    "price-plan-0": 2.50,
    "price-plan-1": 0.50,
    "price-plan-2": 0.25
  }
}
```

### Responses

- `200 OK`
- `404 NOT_FOUND` - unknown meter or no readings

---

## 4. GET /price-plans/recommend/{smartMeterId}?limit={n}

Returns plans sorted by ascending cost.

### Request

```http
GET /price-plans/recommend/smart-meter-0?limit=2
```

### Success response

```json
[
  { "key": "price-plan-2", "value": 0.25 },
  { "key": "price-plan-1", "value": 0.50 }
]
```

### Responses

- `200 OK`
- `400 BAD_REQUEST` - invalid `limit` (<= 0)
- `404 NOT_FOUND` - unknown meter or no readings

---

## Error response schema

```json
{
  "httpStatus": 404,
  "code": "NOT_FOUND",
  "message": "Smart meter not found: smart-meter-x",
  "details": null,
  "timestamp": "2026-06-10T05:30:12.345Z"
}
```

`errorId` is **not** a JSON field in the current API response model.
