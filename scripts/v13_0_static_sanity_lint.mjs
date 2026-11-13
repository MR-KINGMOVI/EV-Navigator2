#!/usr/bin/env node
/**
 * EV Navigator Iran — V13.0 Static Source Sanity & Architecture Lint
 *
 * NOTE: This script performs STATIC source/structure verification only.
 * It is NOT a substitute for Gradle/Kotlinc JVM compilation (`./gradlew testDebugUnitTest`),
 * which requires a JDK installation (`java` / `javac`).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, '..');

function walkFiles(dir, extFilter, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === '.git' || entry.name === '.gradle' || entry.name === 'build' || entry.name === 'node_modules') {
      continue;
    }
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walkFiles(full, extFilter, out);
    } else if (extFilter(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

const ktFiles = walkFiles(rootDir, (name) => name.endsWith('.kt'));
const failures = [];
const checks = [];

function recordCheck(id, description, passed, detail = '') {
  checks.push({ id, description, passed, detail });
  if (!passed) failures.push(`[${id}] ${description}: ${detail}`);
}

// 1. Check duplicate package declarations in any Kotlin file
const dupPkgFiles = [];
for (const file of ktFiles) {
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  const pkgLines = lines.filter((l) => /^package\s+[a-zA-Z0-9_.]+\s*$/.test(l.trim()));
  if (pkgLines.length > 1) {
    dupPkgFiles.push(path.relative(rootDir, file));
  }
}
recordCheck(
  'STATIC-01',
  'Zero duplicate package declarations across all Kotlin source/test files',
  dupPkgFiles.length === 0,
  dupPkgFiles.join(', ')
);

// 2. Check no duplicate ChargingStationRepository in :data (V13.0 Item 8)
const legacyDupRepoPath = path.join(
  rootDir,
  'data/src/main/java/com/evnavigator/iran/data/charging/ChargingStationRepository.kt'
);
const canonicalRepoPath = path.join(
  rootDir,
  'data/src/main/java/com/evnavigator/iran/data/charging/repository/ChargingStationRepository.kt'
);
recordCheck(
  'STATIC-02',
  'Single canonical ChargingStationRepository in :data (duplicate root interface removed)',
  !fs.existsSync(legacyDupRepoPath) && fs.existsSync(canonicalRepoPath),
  `legacyExists=${fs.existsSync(legacyDupRepoPath)}, canonicalExists=${fs.existsSync(canonicalRepoPath)}`
);

// 3. Check Valhalla & GraphHopper geometry decoding and zero straight-line fallback (V13.0 Items 1 & 2)
const valhallaPath = path.join(
  rootDir,
  'core/routing/src/main/java/com/evnavigator/iran/core/routing/provider/ValhallaRoutingProvider.kt'
);
const ghPath = path.join(
  rootDir,
  'core/routing/src/main/java/com/evnavigator/iran/core/routing/provider/GraphHopperRoutingProvider.kt'
);
const valhallaSrc = fs.existsSync(valhallaPath) ? fs.readFileSync(valhallaPath, 'utf8') : '';
const ghSrc = fs.existsSync(ghPath) ? fs.readFileSync(ghPath, 'utf8') : '';
const valhallaValid =
  valhallaSrc.includes('PolylineCodec.decode(encodedShape, precision = 6)') &&
  valhallaSrc.includes('RoutingError.RouteGeometryMissing') &&
  !valhallaSrc.includes('mutableListOf(origin, destination)');
const ghValid =
  ghSrc.includes('RoutingError.RouteGeometryMissing') &&
  !ghSrc.includes('polyline.add(origin)');
recordCheck(
  'STATIC-03',
  'Valhalla decodes precision-6 shape and both Valhalla/GraphHopper forbid 2-point straight-line fallback',
  valhallaValid && ghValid,
  `valhallaValid=${valhallaValid}, ghValid=${ghValid}`
);

// 4. Check InputOrigin.DERIVED_FROM_ROUTE_TIME & SegmentPhysicsEnergyCalculator strict/no-1200m invariants (Items 11-13)
const segCalcPath = path.join(
  rootDir,
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/SegmentPhysicsEnergyCalculator.kt'
);
const segCalcSrc = fs.existsSync(segCalcPath) ? fs.readFileSync(segCalcPath, 'utf8') : '';
const segCalcValid =
  segCalcSrc.includes('InputOrigin.DERIVED_FROM_ROUTE_TIME') &&
  segCalcSrc.includes('simulateLegEnergyStrict') &&
  !segCalcSrc.includes('1200.0');
recordCheck(
  'STATIC-04',
  'SegmentPhysicsEnergyCalculator uses DERIVED_FROM_ROUTE_TIME, supports STRICT mode, and never injects 1200.0m fake altitude',
  segCalcValid,
  segCalcValid ? 'OK' : 'Missing DERIVED_FROM_ROUTE_TIME / simulateLegEnergyStrict or contains 1200.0'
);

// 5. Check :core:weather wind convention, thermal profile, unknown speed/weather, and build.gradle.kts decoupling (Items 14-20, 55)
const atmosPath = path.join(
  rootDir,
  'core/weather/src/main/java/com/evnavigator/iran/core/weather/IranAtmosphericEngine.kt'
);
const trafficIntPath = path.join(
  rootDir,
  'core/weather/src/main/java/com/evnavigator/iran/core/weather/TrafficRouteIntegration.kt'
);
const tripIntPath = path.join(
  rootDir,
  'core/weather/src/main/java/com/evnavigator/iran/core/weather/TripPlannerIntegration.kt'
);
const weatherGradlePath = path.join(rootDir, 'core/weather/build.gradle.kts');
const atmosSrc = fs.existsSync(atmosPath) ? fs.readFileSync(atmosPath, 'utf8') : '';
const trafficIntSrc = fs.existsSync(trafficIntPath) ? fs.readFileSync(trafficIntPath, 'utf8') : '';
const tripIntSrc = fs.existsSync(tripIntPath) ? fs.readFileSync(tripIntPath, 'utf8') : '';
const weatherGradleSrc = fs.existsSync(weatherGradlePath) ? fs.readFileSync(weatherGradlePath, 'utf8') : '';

const weatherValid =
  atmosSrc.includes('enum class WindDirectionConvention') &&
  atmosSrc.includes('METEOROLOGICAL_FROM') &&
  atmosSrc.includes('VECTOR_TO') &&
  atmosSrc.includes('data class VehicleThermalProfile') &&
  !trafficIntSrc.includes('?: 90') &&
  !tripIntSrc.includes('?: 20.0') &&
  !weatherGradleSrc.includes(':core:tripplanner');
recordCheck(
  'STATIC-05',
  'Weather engine enforces WindDirectionConvention, VehicleThermalProfile, no ?: 90 / ?: 20.0 fallbacks, and decouples :core:tripplanner',
  weatherValid,
  weatherValid ? 'OK' : 'Weather invariant check failed'
);

// 6. Check Navigation Patch<T> Clear semantics and Reducer clear support (Items 43 & 105)
const navPatchPath = path.join(
  rootDir,
  'domain/src/main/java/com/evnavigator/iran/domain/model/navigation/NavigationPatch.kt'
);
const reducerPath = path.join(
  rootDir,
  'navigation/src/main/java/com/evnavigator/iran/navigation/engine/impl/NavigationStateReducerImpl.kt'
);
const navPatchSrc = fs.existsSync(navPatchPath) ? fs.readFileSync(navPatchPath, 'utf8') : '';
const reducerSrc = fs.existsSync(reducerPath) ? fs.readFileSync(reducerPath, 'utf8') : '';
const navValid =
  navPatchSrc.includes('sealed class Patch<out T>') &&
  navPatchSrc.includes('data object Clear : Patch<Nothing>()') &&
  reducerSrc.includes('clearNextChargingStop') &&
  reducerSrc.includes('reducePatch');
recordCheck(
  'STATIC-06',
  'NavigationPatch defines Patch<T> (Unchanged/Set/Clear) and NavigationStateReducerImpl clears nullable fields',
  navValid,
  navValid ? 'OK' : 'Missing Patch<T> or clearNextChargingStop in reducer'
);

// 7. Check line-count ceiling (<= 480 lines) for refactored orchestrators and providers
const ceilingTargets = [
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/PredictiveChargingOrchestrator.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/SegmentPhysicsEnergyCalculator.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/energy/CanonicalEnergyEngine.kt',
  'core/routing/src/main/java/com/evnavigator/iran/core/routing/provider/ValhallaRoutingProvider.kt',
  'core/routing/src/main/java/com/evnavigator/iran/core/routing/provider/GraphHopperRoutingProvider.kt',
  'core/routing/src/main/java/com/evnavigator/iran/core/routing/provider/OsrmRoutingProvider.kt',
  'core/routing/src/main/java/com/evnavigator/iran/core/routing/network/NetworkRoutingHttpClient.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/journey/multistop/MultiStopWaypointOptimizer.kt'
];
const ceilingViolations = [];
for (const rel of ceilingTargets) {
  const full = path.join(rootDir, rel);
  if (!fs.existsSync(full)) {
    ceilingViolations.push(`${rel}: missing`);
    continue;
  }
  const lineCount = fs.readFileSync(full, 'utf8').split(/\r?\n/).length;
  if (lineCount > 480) {
    ceilingViolations.push(`${rel}: ${lineCount} lines (> 480)`);
  }
}
recordCheck(
  'STATIC-07',
  'Refactored orchestrator and provider files remain under 480 lines',
  ceilingViolations.length === 0,
  ceilingViolations.join(', ')
);

// 8. Check no build/, .gradle/, or local.properties artifacts exist
const forbiddenDirs = [
  path.join(rootDir, 'build'),
  path.join(rootDir, '.gradle'),
  path.join(rootDir, 'app/build'),
  path.join(rootDir, 'local.properties')
].filter((p) => fs.existsSync(p));
recordCheck(
  'STATIC-08',
  'Zero build/, .gradle/, or local.properties artifacts inside source tree',
  forbiddenDirs.length === 0,
  forbiddenDirs.map((d) => path.relative(rootDir, d)).join(', ')
);

console.log('=== EV NAVIGATOR IRAN V13.0 STATIC SOURCE SANITY LINT ===');
console.log(`Scanned ${ktFiles.length} Kotlin files in ${rootDir}`);
for (const c of checks) {
  console.log(`${c.passed ? 'PASS' : 'FAIL'} [${c.id}] ${c.description}${c.detail ? ` (${c.detail})` : ''}`);
}

if (failures.length > 0) {
  console.error(`\nStatic sanity lint failed with ${failures.length} error(s).`);
  process.exit(1);
} else {
  console.log('\nAll V13.0 static source sanity checks PASSED (Note: JVM/Gradle build remains subject to JDK availability).');
}
