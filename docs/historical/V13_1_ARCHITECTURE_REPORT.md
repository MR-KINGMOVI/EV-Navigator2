# EV Navigator Iran — V13.1 Architecture & Canonical Engine Convergence Report

## 1. Multi-Module Architecture Overview
The project is structured into **12 Gradle modules** with `:domain` serving as a pure-Kotlin Single Source of Truth (SSOT) free of Android framework dependencies:
- **`:domain`:** Canonical physics, energy, charging, travel-time, uncertainty, and route-integrity engines.
- **`:core:routing`:** Valhalla, GraphHopper, OSRM, and offline Contraction Hierarchies routing providers plus polyline decoding and geometry validation.
- **`:data`:** Room persistence, DAOs, `CatalogDomainMappers`, and `IranStationsCatalog` (`2026.4-V13.1`).
- **`:feature` & `:app`:** Jetpack Compose UI, ViewModels, Hilt DI (`IntelligenceModule`), and Android Auto `CarAppService`.

## 2. Canonical Engine Convergence (Section 1)
All parallel or duplicate calculation paths have been converged onto canonical engines in `:domain`:
1. **Energy & Charging Physics (`CanonicalEnergyEngine` & `DefaultEnergyPredictionService`):**
   - `SegmentPhysicsEnergyCalculator` delegates segment energy Wh and SoC-taper charging duration to `CanonicalEnergyEngine`.
   - `StationPortEligibilityEvaluator` delegates port connector compatibility and AC/DC power limit evaluation to `CanonicalEnergyEngine.ConnectorCompatibilityEngine.evaluateDomainPort`.
2. **Travel Time & ETA (`CanonicalTravelTimeEngine`):**
   - Connected directly to `TripEnergyAndFeasibilityOrchestrator`, `PredictiveChargingOrchestrator`, `MultiStopRouteCoordinator`, and provided in `IntelligenceModule`.
3. **Route Geometry & Speed Provenance (`RouteIntegrityValidator`):**
   - `domain/.../intelligence/route/RouteIntegrityValidator.kt` and `core/routing/.../validation/RouteIntegrityValidator.kt` enforce endpoint contiguity, reversal detection (`ROUTE_GEOMETRY_REVERSED`), summary-vs-geometry distance consistency, and strict separation of legal `speedLimitKmh` (`0`/`null` when unknown) from operational cruise speed.

## 3. Large File Technical Justification (> 500 Lines)
All core intelligence and routing orchestrators introduced or refactored in V13.1 are concise and modular (`RouteIntegrityValidator.kt`: 255 lines, `CanonicalTravelTimeEngine.kt`: 185 lines, `StationPortEligibilityEvaluator.kt`: 293 lines, `PredictiveChargingOrchestrator.kt`: 442 lines, `TripEnergyAndFeasibilityOrchestrator.kt`: 347 lines). Static catalog seed tables (`IranFleetVehiclesCatalog.kt`, `IranVehiclesCatalog.kt`, `IranStationsCatalog.kt`) exceed 500 lines solely because they store structured static data records for vehicles and charging stations across Iran.
