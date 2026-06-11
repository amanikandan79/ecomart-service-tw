# Decision Log

## 2026-06-10 - Documentation synchronization baseline

### Context
Repository markdown documents had drift from implementation (endpoint count, error schema, and test status).

### Decision
Use code as source of truth and align docs to:
- 4 implemented endpoints
- error response fields in `ErrorResponse` record (`httpStatus`, `code`, `message`, `details`, `timestamp`)
- current test reality (`test` passes, `functionalTest` currently fails due package name)

### Rationale
Operational and API docs must reflect executable behavior to avoid support and integration mistakes.

### Follow-up
Fix functional test package declaration to restore `functionalTest` green status.
