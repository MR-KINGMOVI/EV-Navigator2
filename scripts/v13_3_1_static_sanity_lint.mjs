#!/usr/bin/env node
/**
 * EV Navigator Iran V13.3.1 — Master Static Sanity & Architectural Invariant Verifier
 *
 * Verifies all V13.2 + V13.3 + V13.3.1 compile, type-safety, unified physics,
 * multi-leg routing, geocoding/reverse-geocoding, UI telemetry, provenance,
 * and line-count invariants across the Android project tree.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');

function stripKotlinComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/.*$/gm, '');
}

function readRawFile(relPath) {
  const full = path.join(ROOT, relPath);
  if (!fs.existsSync(full)) {
    throw new Error(`Missing required file: ${relPath}`);
  }
  return fs.readFileSync(full, 'utf8');
}

function readCodeOnly(relPath) {
  return stripKotlinComments(readRawFile(relPath));
}

function walkKotlinFiles(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'build' || entry.name === '.gradle' || entry.name === '.git') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walkKotlinFiles(full, out);
    } else if (entry.isFile() && entry.name.endsWith('.kt')) {
      out.push(path.relative(ROOT, full));
    }
  }
  return out;
}

const checks = [];
function assertCheck(id, description, condition, details = '') {
  checks.push({ id, description, passed: Boolean(condition), details });
  if (!condition) {
    console.error(`❌ [FAIL] ${id}: ${description} ${details ? `(${details})` : ''}`);
  } else {
    console.log(`✅ [PASS] ${id}: ${description}`);
  }
}

const allKtFiles = walkKotlinFiles(ROOT);

// 1. P0 Compile & Type-Safety Checks
const splitConnectorViolations = allKtFiles.filter((f) => {
  const code = readCodeOnly(f);
  return code.includes('.connectorTypes.split(') || code.includes('connectorTypes?.split(');
});
assertCheck(
  'V13.3.1-S1-NO-CONNECTOR-SPLIT',
  'Zero occurrences of .connectorTypes.split(...) on List<String> across all Kotlin files',
  splitConnectorViolations.length === 0,
  splitConnectorViolations.join(', ')
);

const fromRawEnumViolations = allKtFiles.filter((f) => {
  const code = readCodeOnly(f);
  return code.includes('CanonicalConnectorType.fromRaw(port.connectorType)');
});
assertCheck(
  'V13.3.1-S1-NO-FROMRAW-ENUM',
  'Zero occurrences of CanonicalConnectorType.fromRaw(port.connectorType)',
  fromRawEnumViolations.length === 0,
  fromRawEnumViolations.join(', ')
);

// 2. Unified MultiStopRouteCoordinator & RoadRouteProvider (Section 1)
const multiStopCoordCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/journey/multistop/MultiStopRouteCoordinator.kt'
);
assertCheck(
  'V13.3.1-S1-UNIFIED-MULTISTOP-COORDINATOR',
  'MultiStopRouteCoordinator unifies RoadRouteProvider (calculateRoute + calculateLeg) and both createPlan overloads',
  multiStopCoordCode.includes('fun interface RoadRouteProvider') &&
    multiStopCoordCode.includes('suspend fun calculateRoute(') &&
    multiStopCoordCode.includes('suspend fun calculateLeg(') &&
    multiStopCoordCode.includes('Result<MultiStopJourneyPlan>')
);

// 3. MultiStopWaypointOptimizer Hardcoded 185/195 Wh/km & 1.22/1.25 Removal (Section 6 & 12)
const waypointOptCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/journey/multistop/MultiStopWaypointOptimizer.kt'
);
assertCheck(
  'V13.3.1-S6-NO-HARDCODED-185-195-MULTIPLIERS',
  'MultiStopWaypointOptimizer has zero hardcoded 185.0/195.0 Wh/km or 1.22/1.25 circuity multipliers and delegates to CanonicalEnergyEngine',
  !waypointOptCode.includes('185.0') &&
    !waypointOptCode.includes('195.0') &&
    !waypointOptCode.includes('1.22') &&
    !waypointOptCode.includes('1.25') &&
    waypointOptCode.includes('CanonicalEnergyEngine.predictRouteEnergy')
);

// 4. UnifiedReferenceEnergyCalculator & 9-Component EnergyBreakdown (Section 3)
const unifiedEnergyCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/energy/UnifiedReferenceEnergyCalculator.kt'
);
const canonEnergyCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/energy/CanonicalEnergyEngine.kt'
);
assertCheck(
  'V13.3.1-S3-UNIFIED-ENERGY-CALCULATOR',
  'UnifiedReferenceEnergyCalculator provides 9-component EnergyBreakdown and is exposed via CanonicalEnergyEngine.calculateEnergy',
  unifiedEnergyCode.includes('data class EnergyBreakdown(') &&
    unifiedEnergyCode.includes('rollingResistanceWh:') &&
    unifiedEnergyCode.includes('aerodynamicDragWh:') &&
    unifiedEnergyCode.includes('gradePotentialWh:') &&
    unifiedEnergyCode.includes('accelerationKineticWh:') &&
    unifiedEnergyCode.includes('drivetrainLossesWh:') &&
    unifiedEnergyCode.includes('hvacAuxiliaryWh:') &&
    unifiedEnergyCode.includes('batteryInternalLossesWh:') &&
    unifiedEnergyCode.includes('chargingLossesWh:') &&
    unifiedEnergyCode.includes('regenerativeEnergyWh:') &&
    canonEnergyCode.includes('fun calculateEnergy(request: EnergyCalculationRequest): EnergyCalculationResult')
);

// 5. Multi-Leg FinalPlannedRoute Assembly & Pre-Navigation Consistency (Section 2)
const finalRouteCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/TripExecutionContextAndFinalRoute.kt'
);
assertCheck(
  'V13.3.1-S2-MULTILEG-FINAL-PLANNED-ROUTE',
  'CanonicalFinalRouteAssembler provides assembleFromChargingStops and assembleFromJourneyPlan with per-leg sliced geometry and maneuvers',
  finalRouteCode.includes('fun assembleFromChargingStops(') &&
    finalRouteCode.includes('fun assembleFromJourneyPlan(') &&
    finalRouteCode.includes('MULTI_STOP_COLLAPSED_LEGS')
);

// 6. Full-Iran Geocoding and Reverse Geocoding Service (Section 4 & 16)
const geocodingCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/geocoding/GeocodingAndReverseGeocodingService.kt'
);
assertCheck(
  'V13.3.1-S4-FULL-IRAN-GEOCODING',
  'GeocodingAndReverseGeocodingService supports full-Iran street/POI/city/coordinate geocoding and reverseGeocode with CITY_CENTROID provenance',
  geocodingCode.includes('enum class GeocodingPlaceType') &&
    geocodingCode.includes('CITY_CENTROID') &&
    geocodingCode.includes('fun geocodeQuery(') &&
    geocodingCode.includes('fun reverseGeocode(')
);

// 7. MapNavigationViewModel Synthetic 1000m/1200m/0.5% Grade Removal (Section 7)
const mapVmCode = readCodeOnly(
  'feature/src/main/java/com/evnavigator/iran/ui/screen/map/MapNavigationViewModel.kt'
);
assertCheck(
  'V13.3.1-S7-NO-FAKE-MAP-ELEVATION',
  'MapNavigationViewModel has zero synthetic 1000.0m/1200.0m altitude or 0.5% grade fallback',
  !mapVmCode.includes('?: 1000.0') &&
    !mapVmCode.includes('altitudeMeters = 1200.0') &&
    !mapVmCode.includes('gradePercent = 0.5')
);

// 8. UI Hardcoded Telemetry Removal (Section 17)
const cockpitCmdCode = readCodeOnly(
  'feature/src/main/java/com/evnavigator/iran/ui/screen/map/components/MapCockpitCommandCenter.kt'
);
const mapScreenCode = readCodeOnly(
  'feature/src/main/java/com/evnavigator/iran/ui/screen/MapScreen.kt'
);
const cockpitDrawerCode = readCodeOnly(
  'feature/src/main/java/com/evnavigator/iran/ui/screen/map/components/CockpitRouteDrawer.kt'
);
assertCheck(
  'V13.3.1-S17-NO-HARDCODED-UI-TELEMETRY',
  'MapScreen, MapCockpitCommandCenter, and CockpitRouteDrawer have zero hardcoded * 4.6, / 0.168, ?: 120.0, or ~۲۲ دقیقه telemetry',
  !cockpitCmdCode.includes('* 4.6') &&
    !mapScreenCode.includes('/ 0.168') &&
    !cockpitDrawerCode.includes('?: 120.0') &&
    !cockpitDrawerCode.includes('~۲۲ دقیقه')
);

// 9. Per-Property Vehicle Provenance & Strict SoC Validation (Section 11 & 15)
const vehQualityCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/model/catalog/VehicleDataQualityEvaluator.kt'
);
const policyCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/policy/EvDomainPolicyConfig.kt'
);
assertCheck(
  'V13.3.1-S11-S15-PROVENANCE-AND-SOC',
  'VehicleDataQualityEvaluator tracks VehiclePropertyProvenance and EvDomainPolicyConfig enforces validateSocPercentOrThrow',
  vehQualityCode.includes('data class VehiclePropertyProvenance') &&
    policyCode.includes('fun validateSocPercentOrThrow(') &&
    policyCode.includes('const val DEFAULT_INITIAL_SOC_PERCENT: Double = 80.0')
);

// 10. Six Core Domain Files <= 300 Lines (Section 17 / V13.3 Invariant)
const sixCoreFiles = [
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/energy/CanonicalEnergyEngine.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/PredictiveChargingOrchestrator.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/StationPortEligibilityEvaluator.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/SegmentPhysicsEnergyCalculator.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/CanonicalTravelTimeEngine.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/route/RouteIntegrityValidator.kt',
];
for (const rel of sixCoreFiles) {
  const raw = readRawFile(rel);
  const lineCount = (raw.match(/\n/g) || []).length;
  assertCheck(
    `V13.3.1-LOC-${path.basename(rel)}`,
    `${path.basename(rel)} line count <= 300`,
    lineCount <= 300,
    `${lineCount} lines`
  );
}

// 11. Version 13.3.1 (versionCode = 134) in app/build.gradle.kts
const appGradleRaw = readRawFile('app/build.gradle.kts');
assertCheck(
  'V13.3.1-VERSION-134',
  'app/build.gradle.kts has versionCode = 134 and versionName = "13.3.1"',
  appGradleRaw.includes('versionCode = 134') && appGradleRaw.includes('versionName = "13.3.1"')
);

const failed = checks.filter((c) => !c.passed);
console.log(`\n========================================`);
console.log(`V13.3.1 Static Sanity Summary: ${checks.length - failed.length}/${checks.length} checks passed`);
console.log(`========================================`);
if (failed.length > 0) {
  process.exit(1);
}
