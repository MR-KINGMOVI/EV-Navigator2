# EV Navigator Iran — V13.1 Known Limitations & Operational Risks

## 1. Container Build & Test Environment Limitation (`BLOCKED_NO_JDK_IN_CONTAINER`)
- **Limitation:** The AI Studio container environment does not include a Java Development Kit (`java` / `javac` JDK 17) or the Android SDK (`compileSdk = 34`).
- **Impact:** `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, and `./gradlew lintDebug` cannot be executed inside this container.
- **Mitigation:** An automated Node.js static sanity and AST-level audit script (`scripts/v13_1_static_sanity_lint.mjs`) verifies all 813 Kotlin files for zero fake fallbacks (`60.0` kWh, `11.0` kW AC, `defaultMaxPowerKw` DC, `speedLimitKmh = 90/100`), balanced delimiters, package/import consistency, and presence of all V13.1 behavioral test suites. Full Gradle compilation and JUnit execution must be run on a developer workstation or CI runner equipped with JDK 17 and Android SDK 34.

## 2. Offline Station Catalog Live Occupancy (`port.status = "UNKNOWN"`)
- **Limitation:** Until a live OCPP / Meko backend telemetry feed is connected at runtime, static seed ports in `IranStationsCatalog` (`2026.4-V13.1`) default to `status = "UNKNOWN"`.
- **Impact:** By strict design (Section 7 & Section 8), any charging stop whose port status is `"UNKNOWN"` is capped at `PlanRecommendationStatus.NEEDS_VERIFICATION` (`confidence <= 0.68`) and never reported as `VERIFIED_FEASIBLE`.

## 3. Offline DEM Elevation Resolution
- **Limitation:** When offline SRTM/DEM tiles are not installed for a remote corridor, segment elevations are `null`.
- **Impact:** `DefaultEnergyPredictionService` and `SegmentPhysicsEnergyCalculator` add explicit DEM grade uncertainty (`totalDemUncertaintyWh`) and cap route confidence at `LOW_CONFIDENCE` / `ESTIMATED`.
