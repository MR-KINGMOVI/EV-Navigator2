#!/usr/bin/env node
/**
 * EV Navigator Iran V13.2 — Reproducible Static Sanity, Data Provenance & Behavioral Invariant Verifier
 * Located inside `scripts/v13_2_static_sanity_lint.mjs` within the project archive (`EV_Navigator_Iran_V13_2_FINAL.zip`).
 *
 * NOTE: This static sanity verifier inspects Kotlin source files, braces/imports, version alignment,
 * and V13.2 anti-fabrication invariants. It is NOT a substitute for `./gradlew assembleDebug` or
 * `./gradlew testDebugUnitTest` (which require Android SDK + Gradle distribution network access).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = fs.existsSync(path.join(__dirname, '..', 'settings.gradle.kts'))
  ? path.resolve(__dirname, '..')
  : path.resolve(__dirname, '..', 'uploaded_zip', 'extracted');

function walkFiles(dir, extFilter = '.kt', results = []) {
  if (!fs.existsSync(dir)) return results;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'build' || entry.name === '.gradle' || entry.name === '.git' || entry.name === 'node_modules') {
      continue;
    }
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walkFiles(fullPath, extFilter, results);
    } else if (entry.name.endsWith(extFilter)) {
      results.push(fullPath);
    }
  }
  return results;
}

function stripKotlinComments(src) {
  let out = '';
  let inString = false;
  let inChar = false;
  let inTriple = false;
  let inLineComment = false;
  let inBlockComment = false;
  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    const next = src[i + 1] || '';
    const next2 = src[i + 2] || '';
    if (inLineComment) {
      if (ch === '\n') {
        inLineComment = false;
        out += '\n';
      }
      continue;
    }
    if (inBlockComment) {
      if (ch === '*' && next === '/') {
        inBlockComment = false;
        i++;
      } else if (ch === '\n') {
        out += '\n';
      }
      continue;
    }
    if (inTriple) {
      out += ch;
      if (ch === '"' && next === '"' && next2 === '"') {
        out += '""';
        inTriple = false;
        i += 2;
      }
      continue;
    }
    if (inString) {
      out += ch;
      if (ch === '\\') {
        if (next) out += next;
        i++;
        continue;
      }
      if (ch === '"') inString = false;
      continue;
    }
    if (inChar) {
      out += ch;
      if (ch === '\\') {
        if (next) out += next;
        i++;
        continue;
      }
      if (ch === "'") inChar = false;
      continue;
    }
    if (ch === '/' && next === '/') {
      inLineComment = true;
      i++;
      continue;
    }
    if (ch === '/' && next === '*') {
      inBlockComment = true;
      i++;
      continue;
    }
    if (ch === '"' && next === '"' && next2 === '"') {
      inTriple = true;
      out += '"""';
      i += 2;
      continue;
    }
    if (ch === '"') {
      inString = true;
      out += ch;
      continue;
    }
    if (ch === "'") {
      inChar = true;
      out += ch;
      continue;
    }
    out += ch;
  }
  return out;
}

function countWcLines(content) {
  const matches = content.match(/\n/g);
  return matches ? matches.length : (content.length > 0 ? 1 : 0);
}

const checks = [];
function assertCheck(id, description, condition, details = '') {
  checks.push({ id, description, passed: Boolean(condition), details });
}

// 1. Scan all Kotlin files for balanced braces and valid package declaration
const allKotlinFiles = walkFiles(projectRoot, '.kt');
let unbalancedFiles = [];
for (const file of allKotlinFiles) {
  const src = fs.readFileSync(file, 'utf8');
  let depth = 0;
  let inString = false;
  let inChar = false;
  let inTripleString = false;
  let inLineComment = false;
  let inBlockComment = false;
  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    const next = src[i + 1] || '';
    const next2 = src[i + 2] || '';
    if (inLineComment) {
      if (ch === '\n') inLineComment = false;
      continue;
    }
    if (inBlockComment) {
      if (ch === '*' && next === '/') {
        inBlockComment = false;
        i++;
      }
      continue;
    }
    if (inTripleString) {
      if (ch === '"' && next === '"' && next2 === '"') {
        inTripleString = false;
        i += 2;
      }
      continue;
    }
    if (inString) {
      if (ch === '\\') {
        i++;
        continue;
      }
      if (ch === '"') inString = false;
      continue;
    }
    if (inChar) {
      if (ch === '\\') {
        i++;
        continue;
      }
      if (ch === "'") inChar = false;
      continue;
    }
    if (ch === '/' && next === '/') {
      inLineComment = true;
      i++;
      continue;
    }
    if (ch === '/' && next === '*') {
      inBlockComment = true;
      i++;
      continue;
    }
    if (ch === '"' && next === '"' && next2 === '"') {
      inTripleString = true;
      i += 2;
      continue;
    }
    if (ch === '"') {
      inString = true;
      continue;
    }
    if (ch === "'") {
      inChar = true;
      continue;
    }
    if (ch === '{') depth++;
    else if (ch === '}') depth--;
  }
  if (depth !== 0) {
    unbalancedFiles.push(`${path.relative(projectRoot, file)} (depth=${depth})`);
  }
}
assertCheck(
  'KT_BRACE_BALANCE',
  `All ${allKotlinFiles.length} Kotlin files have balanced lexical braces`,
  unbalancedFiles.length === 0,
  unbalancedFiles.join(', ')
);

// 2. Validate supported application release versions in app/build.gradle.kts
const appGradlePath = path.join(projectRoot, 'app', 'build.gradle.kts');
const appGradle = fs.readFileSync(appGradlePath, 'utf8');
const supportedAppVersions = [
  { code: 132, name: '13.2.0' },
  { code: 133, name: '13.3.0' },
  { code: 134, name: '13.3.1' },
];
const versionCheckPassed = supportedAppVersions.some(
  version =>
    appGradle.includes(`versionCode = ${version.code}`) &&
    appGradle.includes(`versionName = "${version.name}"`)
);
assertCheck(
  'VERSION_SUPPORTED_RELEASE',
  'app/build.gradle.kts defines a supported versionCode/versionName pair',
  versionCheckPassed
);

// 3. Section 1: OsrmOnlineRouteClient.kt & RealRoadModels.kt have no fake distance/duration/100kmh/maneuver fallbacks
const osrmClientPath = path.join(
  projectRoot,
  'core/routing/src/main/java/com/evnavigator/iran/core/routing/engine/OsrmOnlineRouteClient.kt'
);
const realRoadModelsPath = path.join(
  projectRoot,
  'core/routing/src/main/java/com/evnavigator/iran/core/routing/model/RealRoadModels.kt'
);
const osrmCode = stripKotlinComments(fs.readFileSync(osrmClientPath, 'utf8'));
const realRoadModelsCode = stripKotlinComments(fs.readFileSync(realRoadModelsPath, 'utf8'));
assertCheck(
  'SEC1_OSRM_NO_FAKE_FALLBACKS',
  'OsrmOnlineRouteClient.kt eliminates polyline.size * 50.0, polyline.size * 2.0, road-name speed guessing, and fake maneuver creation',
  !osrmCode.includes('polyline.size * 50.0') &&
    !osrmCode.includes('polyline.size * 2.0') &&
    !osrmCode.includes('finalDistanceMeters / 25.0') &&
    !realRoadModelsCode.includes('val speedLimitKmh: Int = 100') &&
    osrmCode.includes('INSUFFICIENT_ROUTE_DETAILS')
);

// 4. Section 2 & 14: No hidden 90.0 km/h speed or 22.0 C ambient temp fallback in EnergyPredictionService / PredictiveEnergyOrchestrator
const energyServicePath = path.join(
  projectRoot,
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/energy/EnergyPredictionService.kt'
);
const energyOrchestratorPath = path.join(
  projectRoot,
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/energy/PredictiveEnergyOrchestrator.kt'
);
const energyServiceCode = stripKotlinComments(fs.readFileSync(energyServicePath, 'utf8'));
const energyOrchestratorCode = stripKotlinComments(fs.readFileSync(energyOrchestratorPath, 'utf8'));
assertCheck(
  'SEC2_NO_HIDDEN_90_OR_22_FALLBACKS',
  'EnergyPredictionService.kt and PredictiveEnergyOrchestrator.kt eliminate silent 90.0 km/h and 22.0 C defaults and track MODEL_ASSUMPTION + energyUncertaintyRangeWh',
  !energyServiceCode.includes('?: 90.0') &&
    !energyServiceCode.includes('?: 22.0') &&
    !energyOrchestratorCode.includes('ambientTemperatureC: Double = 22.0') &&
    energyServiceCode.includes('InputProvenance.MODEL_ASSUMPTION') &&
    energyOrchestratorCode.includes('energyUncertaintyRangeWh') &&
    energyServiceCode.includes('CanonicalEnergyUnitConverter')
);

// 5. Section 3, 4, 5, 6 & 12: IranStationsCatalog.kt removes LAST_OPERATOR_RELEASE_VERIFIED_AT_EPOCH_MS and bare pricePerKwh numbers
const catalogPath = path.join(
  projectRoot,
  'data/src/main/java/com/evnavigator/iran/data/seed/IranStationsCatalog.kt'
);
const catalogCode = stripKotlinComments(fs.readFileSync(catalogPath, 'utf8'));
assertCheck(
  'SEC3_NO_GLOBAL_VERIFICATION_TIMESTAMP',
  'IranStationsCatalog.kt removes LAST_OPERATOR_RELEASE_VERIFIED_AT_EPOCH_MS = 1762819200000L and uses per-station independent verification records + currency-explicit tariff schedules',
  !catalogCode.includes('LAST_OPERATOR_RELEASE_VERIFIED_AT_EPOCH_MS') &&
    !catalogCode.includes('1762819200000L') &&
    !catalogCode.includes('pricePerKwh = 15000.0') &&
    !catalogCode.includes('pricePerKwh = 12000.0') &&
    !catalogCode.includes('pricePerKwh = 8000.0') &&
    catalogCode.includes('INDEPENDENT_STATION_VERIFICATION_REGISTRY') &&
    catalogCode.includes('UrlVerificationStatus.LINK_UNCHECKED') &&
    catalogCode.includes('TariffCurrency.IRR_RIAL')
);

// 6. Section 15: Verify 6 key domain files remain <= 300 lines (wc -l)
const max300Files = [
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/PredictiveChargingOrchestrator.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/StationPortEligibilityEvaluator.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/SegmentPhysicsEnergyCalculator.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/trip/CanonicalTravelTimeEngine.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/energy/CanonicalEnergyEngine.kt',
  'domain/src/main/java/com/evnavigator/iran/domain/intelligence/route/RouteIntegrityValidator.kt'
];
const lineCountViolations = [];
const lineCountSummary = [];
for (const rel of max300Files) {
  const full = path.join(projectRoot, rel);
  const lines = countWcLines(fs.readFileSync(full, 'utf8'));
  lineCountSummary.push(`${path.basename(rel)}=${lines}`);
  if (lines > 300) {
    lineCountViolations.push(`${rel}: ${lines} lines (> 300)`);
  }
}
assertCheck(
  'SEC15_LINE_COUNTS_UNDER_300',
  `All 6 refactored core domain orchestrator/evaluator files are <= 300 lines (${lineCountSummary.join(', ')})`,
  lineCountViolations.length === 0,
  lineCountViolations.join('; ')
);

// 7. Verify V13.2 behavioral test files exist
const v132RoutingTestPath = path.join(
  projectRoot,
  'core/routing/src/test/java/com/evnavigator/iran/core/routing/V132OsrmAndRoutingBehavioralTest.kt'
);
const v132DomainTestPath = path.join(
  projectRoot,
  'domain/src/test/java/com/evnavigator/iran/domain/intelligence/V132MasterBehavioralAuditTest.kt'
);
assertCheck(
  'SEC10_BEHAVIORAL_TESTS_PRESENT',
  'V132OsrmAndRoutingBehavioralTest.kt and V132MasterBehavioralAuditTest.kt exist',
  fs.existsSync(v132RoutingTestPath) && fs.existsSync(v132DomainTestPath)
);

const failed = checks.filter((c) => !c.passed);
console.log('================================================================================');
console.log('EV Navigator Iran V13.2 — Static Sanity & Provenance Audit Summary');
console.log(`Project Root: ${projectRoot}`);
console.log(`Kotlin Source Files Scanned: ${allKotlinFiles.length}`);
console.log('================================================================================');
for (const c of checks) {
  console.log(`[${c.passed ? 'PASS' : 'FAIL'}] ${c.id}: ${c.description}${c.details ? ' -> ' + c.details : ''}`);
}
console.log('--------------------------------------------------------------------------------');
console.log('IMPORTANT ENVIRONMENT LIMITATION:');
console.log('Gradle Android build (`./gradlew assembleDebug` / `./gradlew testDebugUnitTest`)');
console.log('is BLOCKED / NOT VERIFIED in this container due to offline network restriction');
console.log('(cannot download gradle-8.4-bin.zip from services.gradle.org) and absence of');
console.log('Android SDK platform-34.');
console.log('================================================================================');

if (failed.length > 0) {
  process.exit(1);
}
