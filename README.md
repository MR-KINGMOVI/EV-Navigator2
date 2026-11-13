# EV Navigator Iran — V13.2.0 (`versionCode = 132`)

**Release Gate Status:** `BLOCKED / NOT VERIFIED` (Static Sanity & Provenance Audit: `PASS`; Offline Container Gradle Build/Test Execution: `BLOCKED / NOT VERIFIED`)

## Key Engineering Highlights in V13.2.0
1. **Strict OSRM Route Parsing (`OsrmOnlineRouteClient.kt` & `RealRoadModels.kt`):**
   - Eliminated all synthetic route fallbacks (`polyline.size * 50.0`, `polyline.size * 2.0`, `finalDistanceMeters / 25.0`, road-name speed guessing, `speedLimitKmh = 100`, and fake `RealRoadManeuver` creation).
   - Routes with geometry but missing `steps` are returned as `OsrmRouteCompleteness.INSUFFICIENT_ROUTE_DETAILS` with `hasTurnByTurnGuidance = false` and `0` maneuvers.
2. **Elimination of Hidden `90.0 km/h` & `22.0 °C` Fallbacks (`EnergyPredictionService.kt` & `PredictiveEnergyOrchestrator.kt`):**
   - Reference model assumptions are explicitly tagged as `MODEL_ASSUMPTION` with reduced confidence and propagated `energyUncertaintyRangeWh`.
3. **Per-Station Independent Verification & Explicit Currency Tariffs (`IranStationsCatalog.kt` & `ChargingTariffAndProvenanceModels.kt`):**
   - Removed global `LAST_OPERATOR_RELEASE_VERIFIED_AT_EPOCH_MS = 1762819200000L`.
   - Each station uses independent verification records, `UrlVerificationStatus.LINK_UNCHECKED`, automatic `STALE` transition after TTL expiry, and currency-explicit `ChargingTariffSchedule` (`IRR_RIAL` / `IRT_TOMAN`).
4. **Reproducible Static Sanity Check (`scripts/v13_2_static_sanity_lint.mjs`):**
   - Run `node scripts/v13_2_static_sanity_lint.mjs` from the project root.
