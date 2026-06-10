# Endpoint Verification

Synced against controllers on 2026-06-10.

## Implemented endpoints

| Endpoint | Method | Controller method |
|---|---|---|
| `/readings/store` | POST | `MeterReadingController.storeReadings` |
| `/readings/read/{smartMeterId}` | GET | `MeterReadingController.readReadings` |
| `/price-plans/compare-all/{smartMeterId}` | GET | `PricePlanComparatorController.calculatedCostForEachPricePlan` |
| `/price-plans/recommend/{smartMeterId}` | GET | `PricePlanComparatorController.recommendCheapestPricePlans` |

## Notes

- `/price-plans` (list all plans) is **not** currently implemented.
- Error response schema uses `httpStatus`, `code`, `message`, `details`, `timestamp`.
