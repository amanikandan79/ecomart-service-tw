Decision: Fix incorrect energy calculation in PricePlanService

Context:
- PricePlanService previously divided average power by elapsed time (avg / hours) when computing energy, producing near-zero values after rounding.
- This was discovered during functional testing where all price-plan costs were reported as 0.0.

Options considered:
1. Multiply average power by time (kW * hours) to compute energy (kWh).  (Correct physics)
2. Keep existing formula but adjust rounding to higher precision to avoid zero. (Band-aid)
3. Rework domain to store cumulative energy instead of power readings. (Large refactor)

Decision taken:
- Implement option (1): energy = average(kW) * time(hours). Multiply by unit rate to compute cost. Keep high internal precision and round final cost to 1 decimal to match test expectations.

Reason:
- Option (1) is physically correct and aligns with domain units (ElectricityReading.reading documented as kW). It produces meaningful non-zero costs and requires minimal change.

Trade-offs & Risks:
- Tests that depended on previous incorrect behavior needed updating.
- Final rounding is set to 1 decimal to align with existing tests; business may require 2 decimals for currency. This can be adjusted easily.

Validation:
- Added PricePlanServiceTest to assert correct energy and cost calculations, including edge cases (single reading, small readings).
- Ran unit and functional tests: all passed.

Next steps:
- Add coverage gate (JaCoCo) to CI.
- Consider mutation testing to prevent similar logical errors.
- Document unit conventions (kW vs kWh) in README and domain classes.

Files changed:
- src/main/java/uk/tw/energy/service/PricePlanService.java (fixed energy calculation)
- src/test/java/uk/tw/energy/service/PricePlanServiceTest.java (new tests)
- src/test/java/uk/tw/energy/controller/PricePlanComparatorControllerTest.java (updated expectations)
- src/test/java/uk/tw/energy/controller/MeterReadingControllerTest.java (HTTP status expectation updates)
- src/functional-test/java/uk/tw/energy/EndpointTest.java (fixed package)

Commit message suggestion:
Fix energy calculation in PricePlanService; add tests and decision log

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>