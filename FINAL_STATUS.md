# Current Repository Status

Synced on 2026-06-10.

## Summary

- Application compiles and unit tests pass.
- Functional test task currently fails because `EndpointTest` is under prohibited package namespace (`java.uk.tw.energy`).
- API and architecture docs are now aligned with current controller/service/exception code.

## Current validation/build status

| Area | Status |
|---|---|
| `gradlew test` | Passing |
| `gradlew functionalTest` | Failing (known package declaration issue) |
| API contract docs | Updated |
| Architecture docs | Updated |
