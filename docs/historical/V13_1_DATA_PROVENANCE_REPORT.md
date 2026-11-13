# EV Navigator Iran — V13.1 Data Provenance & Catalog Trust Report (Section 8)

## 1. Catalog Dataset Version & Schema
- **Dataset Version (`CATALOG_DATASET_VERSION`):** `2026.4-V13.1`
- **Schema Version (`CATALOG_SCHEMA_VERSION`):** `2.3`
- **Last Operator Release Verification Timestamp (`LAST_OPERATOR_RELEASE_VERIFIED_AT_EPOCH_MS`):** `1762819200000L`

## 2. Station Catalog Distribution & Provenance Separation
- **Total Seed Catalog Stations (`IranStationsCatalog.getAllStationsAndPorts(includeSeedCandidates = true)`):** `195` stations
- **Production-Eligible Stations (`IranStationsCatalog.getProductionStationsAndPorts()`):** `102` stations
  - **`VERIFIED` Stations (`10` stations):** Confirmed physical MAPNA (Meko) hubs with explicit external `https://meko.ir/stations/mapna_...` URLs, non-blank `externalId`, and positive `verifiedAt` timestamp.
  - **`PARTIALLY_VERIFIED` Stations (`92` stations):** Municipal and regional EV hubs with internal registry URN (`urn:iran-ev-registry:municipal-hub:...`) stored strictly in `sourceReference` and `sourceUrl = null`.
- **Excluded Seed Survey Candidates (`93` stations):** `UNVERIFIED` corridor survey records (`urn:iran-ev-registry:corridor-survey:...`) excluded by default from production trip planning and Android Auto screens.

## 3. V13.1 Provenance Invariants Enforced in Code
1. **Zero URN-to-URL Synthesis:**
   - `IranStationsCatalog.addStation` never converts an internal URN (`urn:iran-ev-registry:...`) into an external URL (`sourceUrl`).
   - Any station claiming `ValidationStatus.VERIFIED` without an explicit `http://` or `https://` `sourceUrl` and non-blank `externalId` is automatically downgraded to `ValidationStatus.PARTIALLY_VERIFIED`.
2. **Duplicate Station Detection & Evidence-Preserving Merge:**
   - `StationCatalogDeduplicator.deduplicateAndMergeDomainStations` (`CatalogDomainModels.kt`) and `IranStationsCatalog.deduplicateAndMergeStations` (`IranStationsCatalog.kt`) detect duplicate station records by `externalId`, coordinate proximity (`<= 35m`), or normalized Persian name + coordinate proximity (`<= 120m`), merging them while preserving the highest verification rank, traceable external `SourceEvidence`, external URL, and distinct charging ports.
3. **Speed, Battery, and Connector Provenance:**
   - Missing legal road speed limit (`speedLimitKmh`) is preserved as `0` / `null` (`InputOrigin.UNKNOWN`) and never fabricated as `90` or `100` km/h.
   - Missing usable battery capacity (`usableBatteryKwh`) is preserved as `Double.NaN` (`InputProvenance.UNKNOWN`, `dataQuality = "INSUFFICIENT_VEHICLE_DATA"`) and never fabricated as `60.0` kWh.
   - Missing vehicle AC or DC charging power (`vehicleAcMaxKw`, `vehicleDcMaxKw`) is preserved as `null` and never fabricated as `11.0` kW or `defaultMaxPowerKw`.
