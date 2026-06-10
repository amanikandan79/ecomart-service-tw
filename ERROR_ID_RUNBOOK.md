# Error Runbook

## Important

Current API error response does **not** include a dedicated `errorId` field.

Use:
- HTTP status
- `code`
- `message`
- `timestamp`
- server logs around the same timestamp

## Error codes in current implementation

| Code | HTTP | Typical causes |
|---|---:|---|
| `BAD_REQUEST` | 400 | Invalid payload, constraint violations, invalid query parameters |
| `NOT_FOUND` | 404 | Unknown meter ID or missing readings for meter |
| `INTERNAL_SERVER_ERROR` | 500 | Unhandled runtime exception |

## Triage steps

1. Capture full response body and request path.
2. Match by `timestamp` in application logs.
3. Check controller/service logs for the same request path and meter ID.
4. Resolve root cause and retry the request.
