# EV Navigator Iran — V13.1 Behavioral Test & Static Verification Report

## 1. Executive Summary
- **Kotlin JUnit Test Suite Inventory:** `176` test files in `src/test` across `:domain`, `:core`, `:core:routing`, `:core:energy`, `:core:tripplanner`, `:data`, and `:feature`.
- **New V13.1 Behavioral Audit Test Suites Added:**
  1. `domain/src/test/java/com/evnavigator/iran/domain/intelligence/V131MasterBehavioralAuditTest.kt`
  2. `core/routing/src/test/java/com/evnavigator/iran/core/routing/V131RoutingAndGeometryBehavioralTest.kt`
  3. Updated `data/src/test/java/com/evnavigator/iran/data/seed/IranStationsCatalogIntegrityTest.kt`
- **JVM / Gradle Test Execution Status in Container:** `TEST_EXECUTION_BLOCKED_NO_JDK_IN_CONTAINER`
  - **Root Cause:** The Linux container environment (`/app/applet`) has Node.js v22 and Python 3.11 installed, but no `java` / `javac` / JDK 17 or Android SDK (`which java` returns exit code 1). Therefore, `./gradlew test` cannot be executed inside this container.
- **Executed Verification in Container:**
  1. **Node.js V13.1 Static Sanity & AST-Level Audit (`node scripts/v13_1_static_sanity_lint.mjs`):** `PASS` (`0` violations across all `637` production `.kt` files and `176` test `.kt` files).
  2. **Node.js Unit Test Suite (`npx tsx --test src/utils/zipHelpers.test.ts`):** `PASS` (verifies archive integrity, V13.1 deliverables, zero fake fallbacks, and behavioral test coverage).

## 2. V13.1 Behavioral Test Matrix (Section 12)

| Test Class | Test Method | Covered Spec Section | Verified Invariant |
|---|---|---|---|
| `V131MasterBehavioralAuditTest` | `Section 2 - Empty or unrecognized connectors return UNKNOWN and missing AC or DC vehicle power never falls back to 11kW or defaultMaxPowerKw` | Section 2 | Empty/unknown connectors -> `UNKNOWN`; missing `vehicleAcMaxKw` or `vehicleDcMaxKw` -> `effectivePeakPowerKw = null`, `canProduceDeterministicDuration = false` |
| `V131MasterBehavioralAuditTest` | `Section 3 - Missing or invalid usable battery capacity returns INSUFFICIENT_VEHICLE_DATA and never substitutes 60kWh` | Section 3 | `0.0`, `-15.0`, `NaN`, `Infinity` battery capacity -> `INSUFFICIENT_VEHICLE_DATA`, `isFeasibleWithoutCharging = false`; gross-only -> `ESTIMATED` |
| `V131MasterBehavioralAuditTest` | `Section 3 & 4 - ConfidenceAndUncertaintyEngine with unknown battery capacity reports VERY_HIGH_UNCERTAINTY without 60kWh fallback` | Sections 3 & 4 | Missing battery capacity -> `VERY_HIGH_UNCERTAINTY` and `"MISSING_BATTERY_CAPACITY"` risk |
| `V131MasterBehavioralAuditTest` | `Section 5 & 6 - RouteIntegrityValidator separates legal speedLimitKmh from operational speed and detects reversed or mismatched geometry` | Sections 5 & 6 | `rawSpeedLimitKmh = 0` -> `legalSpeedLimitKmh = null` (`UNKNOWN`); reversed polyline -> `ROUTE_GEOMETRY_REVERSED`; gap between segments -> `DISCONTINUOUS_SEGMENTS` |
| `V131MasterBehavioralAuditTest` | `Section 7 - StationPortEligibilityEvaluator boundary conditions around minReserveSoc and port power eligibility` | Section 7 | `arrivalSoc = minReserveSoc - 1` -> `INFEASIBLE`; `arrivalSoc == minReserveSoc` -> `VERIFIED_FEASIBLE`; `arrivalSocMin < minReserveSoc` -> `ESTIMATED_FEASIBLE` |
| `V131MasterBehavioralAuditTest` | `Section 8 - StationCatalogDeduplicator merges duplicate stations preserving external SourceEvidence and ports` | Section 8 | Merges duplicate stations while preserving `VERIFIED` status, external `https://` URL, and both DC & AC ports |
| `V131MasterBehavioralAuditTest` | `Section 9 - CanonicalTravelTimeEngine rejects negative or non-finite inputs and produces consistent breakdown` | Section 9 | Rejects negative charging duration and `NaN` speed (`isValid = false`); verifies exact sum of duration components |
| `V131RoutingAndGeometryBehavioralTest` | `buildContiguousSegmentsFromManeuvers never invents 90 kmh speedLimitKmh when maneuver speed limit is unknown` | Section 5 | Unannotated segments retain `speedLimitKmh = 0` |
| `V131RoutingAndGeometryBehavioralTest` | `validateDecodedRouteGeometry detects reversed route and mismatched endpoints` | Section 6 | Reversed polyline and 4x distance mismatch are rejected deterministically |
| `V131RoutingAndGeometryBehavioralTest` | `Valhalla and GraphHopper providers preserve speedLimitKmh = 0 when backend omits speed limit` | Section 5 | Provider JSON without `speed_limit`/`max_speed` parses with `speedLimitKmh = 0` |
| `IranStationsCatalogIntegrityTest` | `deduplicateAndMergeStations merges duplicate station entities while preserving external URL and ports` | Section 8 | Room entity deduplication preserves external URL and ports |

## 3. Command to Execute Full JUnit Suite on a Machine with JDK 17
```bash
./gradlew testDebugUnitTest :domain:test :core:routing:test :data:test --continue
```
