# Validation Layers Diagram

```text
Client JSON
  -> Controller (@RequestBody)
      -> SmartValidator (MeterReadings + nested ElectricityReading)
          -> Service (@Validated method constraints)
              -> Business logic
                  -> Exception handler (uniform ErrorResponse)
```

## Error response shape

```json
{
  "httpStatus": 400,
  "code": "BAD_REQUEST",
  "message": "Validation failed",
  "details": ["smartMeterId: must not be blank"],
  "timestamp": "2026-06-10T05:30:12.345Z"
}
```
