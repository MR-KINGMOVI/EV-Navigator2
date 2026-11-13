# EV Navigator Iran — V13.1 Baseline Inventory & Audit Snapshot

## 1. Repository & Version Identity
- **Project Name:** EV Navigator Iran
- **Baseline Version Audited:** `13.0.0` (`versionCode = 130`)
- **Target Release Version:** `13.1.0` (`versionCode = 131`, `versionName = "13.1.0"`)
- **Catalog Dataset Version:** `2026.4-V13.1` (`CATALOG_SCHEMA_VERSION = "2.3"`)
- **Gradle Modules (12):** `:app`, `:core`, `:core:energy`, `:core:roadprofile`, `:core:routing`, `:core:tripplanner`, `:core:weather`, `:data`, `:domain`, `:feature`, `:design-system`, `:navigation`

## 2. Exact Source & Test File Inventory (`wc -l` Verified)
- **Total Kotlin Files (`.kt`):** `813`
- **Production Kotlin Files (`src/main`):** `637`
- **Unit Test Kotlin Files (`src/test`):** `176`
- **Static Seed Catalog Entities:**
  - `IranStationsCatalog` + `IranStationsCatalogRegionalExpansion`: `195` total stations (`102` production-eligible `VERIFIED`/`PARTIALLY_VERIFIED` stations, `93` excluded `UNVERIFIED` corridor survey seed candidates)
  - `IranVehiclesCatalog`: `25` primary EV trims + `IranFleetVehiclesCatalog` (`75` extended fleet variants)

## 3. Baseline Findings Identified Before V13.1 Remediation
1. **[P0] Residual `60.0` kWh Battery Capacity Fallbacks:**
   - `EnergyPredictionService.kt` (`VehiclePhysicsParams.assumedReference(usableBatteryKwh = 60.0)` and `DefaultEnergyPredictionService.predict` `?: 60.0`)
   - `ConfidenceAndUncertaintyEngine.kt` (`context.batteryCapacityKwh.value ?: 60.0`)
   - `TripIntelligenceViewModel.kt` (`context.batteryCapacityKwh.value ?: 60.0` and `maxKw ?: 60.0`)
   - `ActiveJourneyScreen.kt` and `StationIntelligenceSheet.kt` (`maxKw ?: 60.0`)
2. **[P0] Residual AC/DC Vehicle Power Fallbacks in `CanonicalEnergyEngine`:**
   - `ConnectorCompatibilityEngine.resolveCompatiblePower` fell back to `?: standard.defaultMaxPowerKw` for DC and `?: min(11.0, standard.defaultMaxPowerKw)` for AC when `vehicleDcMaxKw` or `vehicleAcMaxKw` was `null`.
3. **[P0] Residual `speedLimitKmh = 90` / `100` Fallbacks in Routing:**
   - `core/routing/.../RouteIntegrityValidator.kt` (`speedLimitKmh = 90` on unannotated segments)
   - `ValhallaRoutingProvider.kt`, `GraphHopperRoutingProvider.kt`, and `OsrmRoutingProvider.kt` (`speedLimitKmh = 90` / `100` when backend JSON omitted speed limits)
4. **[P1] Domain Route Integrity Validator & Reversal Detection:**
   - Needed dedicated `domain/.../intelligence/route/RouteIntegrityValidator.kt` enforcing origin/destination endpoint snap validation, reversed geometry detection (`ROUTE_GEOMETRY_REVERSED`), summary-vs-geometry distance ratio validation, and segment contiguity checks (`DISCONTINUOUS_SEGMENTS`).
5. **[P1] `CanonicalTravelTimeEngine` Production Wiring:**
   - `CanonicalTravelTimeEngine` existed in `domain/intelligence/trip/` but needed direct wiring into `TripEnergyAndFeasibilityOrchestrator`, `PredictiveChargingOrchestrator`, `MultiStopRouteCoordinator`, and `IntelligenceModule` DI, plus input validation against negative/`NaN`/`Infinite` values.
6. **[P1] Station Catalog URN-to-URL Synthesis & Duplicate Station Merge:**
   - `IranStationsCatalog.addStation` had a fallback synthesizing `https://meko.ir/stations/...` when `sourceUrl` was an internal URN, and needed `deduplicateAndMergeStations` + `StationCatalogDeduplicator.deduplicateAndMergeDomainStations`.
