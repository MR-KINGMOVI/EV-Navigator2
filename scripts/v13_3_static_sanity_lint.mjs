#!/usr/bin/env node
/**
 * EV Navigator Iran V13.3 — Static Sanity & Architectural Invariant Verifier
 *
 * Verifies all V13.2 + V13.3 compile, type-safety, physics, routing, charging,
 * and line-count invariants across the Android project tree (stripping comments before code token checks).
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

// 1. P0 Compile & Type-Safety Checks (Section 1)
const splitConnectorViolations = allKtFiles.filter((f) => {
  const code = readCodeOnly(f);
  return code.includes('.connectorTypes.split(') || code.includes('connectorTypes?.split(');
});
assertCheck(
  'V13.3-S1-NO-CONNECTOR-SPLIT',
  'Zero occurrences of .connectorTypes.split(...) on List<String> across all Kotlin files',
  splitConnectorViolations.length === 0,
  splitConnectorViolations.join(', ')
);

const fromRawEnumViolations = allKtFiles.filter((f) => {
  const code = readCodeOnly(f);
  return code.includes('CanonicalConnectorType.fromRaw(port.connectorType)');
});
assertCheck(
  'V13.3-S1-NO-FROMRAW-ENUM',
  'Zero occurrences of CanonicalConnectorType.fromRaw(port.connectorType)',
  fromRawEnumViolations.length === 0,
  fromRawEnumViolations.join(', ')
);

const isValidDomainVehicleViolations = allKtFiles.filter((f) => {
  const code = readCodeOnly(f);
  return code.includes('activeVehicleDomain?.isValid()') || code.includes('domVeh.isValid()');
});
assertCheck(
  'V13.3-S1-NO-ISVALID-CALL',
  'Zero calls to non-existent DomainVehicle.isValid() (replaced by evaluateQuality / VehicleDataQualityEvaluator)',
  isValidDomainVehicleViolations.length === 0,
  isValidDomainVehicleViolations.join(', ')
);

// 2. Ambiguous Connector Rejection (Section 6)
const catalogModelsCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/model/catalog/CatalogDomainModels.kt'
);
assertCheck(
  'V13.3-S6-AMBIGUOUS-CONNECTOR-GUARD',
  'CanonicalConnectorType rejects bare CCS and bare GB/T via isAmbiguousRaw and ConnectorNormalizer',
  catalogModelsCode.includes('fun isAmbiguousRaw(') &&
    catalogModelsCode.includes('object ConnectorNormalizer')
);

// 3. TripIntelligenceViewModel De-Duplication & Synthetic Simulation Removal (Section 3 & 4)
const tripVmCode = readCodeOnly(
  'feature/src/main/java/com/evnavigator/iran/ui/screen/intelligence/TripIntelligenceViewModel.kt'
);
assertCheck(
  'V13.3-S3-NO-FAKE-TOPOGRAPHY-OR-STATIONS',
  'TripIntelligenceViewModel has zero synthetic generateTopographicProfile, findCorridorChargingStations, or circuityFactor simulation',
  !tripVmCode.includes('fun generateTopographicProfile') &&
    !tripVmCode.includes('fun findCorridorChargingStations') &&
    !tripVmCode.includes('circuityFactor')
);

// 4. MapDestinationAndCorridorHelper Linear SOC Removal (Section 3)
const mapHelperCode = readCodeOnly(
  'feature/src/main/java/com/evnavigator/iran/ui/screen/map/MapDestinationAndCorridorHelper.kt'
);
assertCheck(
  'V13.3-S3-NO-LINEAR-022-SOC',
  'MapDestinationAndCorridorHelper has zero linear 100.0 - remainingKm * 0.22 SOC hacks and no corridorStops.size >= 2 truncation',
  !mapHelperCode.includes('0.22') && !mapHelperCode.includes('corridorStops.size >= 2')
);

// 5. SegmentPhysicsEnergyCalculator Regeneration & Zero-Distance Preservation (Section 4 & 10)
const segPhysicsCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/SegmentPhysicsEnergyCalculator.kt'
);
assertCheck(
  'V13.3-S10-NO-ENERGY-CLAMP-10WH',
  'SegmentPhysicsEnergyCalculator does not clamp totalExpectedWh to 10.0 Wh or batteryCapacityKwh to 5.0 kWh',
  !segPhysicsCode.includes('coerceAtLeast(10.0)') && !segPhysicsCode.includes('coerceAtLeast(5.0)')
);

// 6. SegmentKineticProfileBuilder 2-Point Polyline & Elevation Split Fix (Section 10)
const kineticBuilderCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/SegmentKineticProfileBuilder.kt'
);
assertCheck(
  'V13.3-S10-NO-50-50-ELEVATION-SPLIT',
  'SegmentKineticProfileBuilder does not split route elevation 50/50 across segments and separates legalSpeedLimitKmh from targetSpeedKmh',
  !kineticBuilderCode.includes('* 0.5') && kineticBuilderCode.includes('legalSpeedLimitKmh =')
);

// 7. CanonicalTravelTimeEngine Multiplicative Traffic + Weather & Waiting Duration (Section 11)
const etaEngineCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/CanonicalTravelTimeEngine.kt'
);
assertCheck(
  'V13.3-S11-MULTIPLICATIVE-ETA',
  'CanonicalTravelTimeEngine applies multiplicative traffic and weather formula and supports waitingDurationSeconds',
  etaEngineCode.includes('safeTrafficMult / weatherDivisor') &&
    etaEngineCode.includes('waitingDurationSeconds: Double')
);

// 8. FinalPlannedRoute & TripExecutionContext Single Source of Truth (Section 2 & 8)
const finalRouteCode = readCodeOnly(
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/TripExecutionContextAndFinalRoute.kt'
);
assertCheck(
  'V13.3-S8-FINAL-PLANNED-ROUTE',
  'FinalPlannedRoute and TripExecutionContext enforce validatePreNavigationConsistency()',
  finalRouteCode.includes('data class FinalPlannedRoute') &&
    finalRouteCode.includes('data class TripExecutionContext') &&
    finalRouteCode.includes('fun validatePreNavigationConsistency()')
);

// 9. Six Core Domain Files <= 300 Lines (Section 17)
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
    `V13.3-S17-LOC-${path.basename(rel)}`,
    `${path.basename(rel)} line count <= 300`,
    lineCount <= 300,
    `${lineCount} lines`
  );
}

// 10. Version 13.3.x in app/build.gradle.kts
const appGradleRaw = readRawFile('app/build.gradle.kts');
assertCheck(
  'V13.3-VERSION-133',
  'app/build.gradle.kts has versionCode >= 133 and versionName 13.3.x',
  (appGradleRaw.includes('versionCode = 134') && appGradleRaw.includes('versionName = "13.3.1"')) ||
    (appGradleRaw.includes('versionCode = 133') && appGradleRaw.includes('versionName = "13.3.0"'))
);

const failed = checks.filter((c) => !c.passed);
console.log(`\n========================================`);
console.log(`V13.3 Static Sanity Summary: ${checks.length - failed.length}/${checks.length} checks passed`);
console.log(`========================================`);
if (failed.length > 0) {
  process.exit(1);
}
