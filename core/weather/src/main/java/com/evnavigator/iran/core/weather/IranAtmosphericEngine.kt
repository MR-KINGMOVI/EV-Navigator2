package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision atmospheric, thermodynamic, and aerodynamic physics engine
 * calibrated for Iran's diverse climatic zones (V13.0 Audit — Items 16–20).
 *
 * Key V13.0 guarantees:
 * 1. Explicit [WindDirectionConvention] (`METEOROLOGICAL_FROM` vs `VECTOR_TO`) so wind blowing FROM North (`0°`)
 *    against a vehicle driving North (`0°`) yields a positive headwind (`+windSpeedKmh`), whereas wind FROM South (`180°`)
 *    yields a tailwind (`-windSpeedKmh`).
 * 2. Vehicle-specific [VehicleThermalProfile] and [VehicleBatteryProfile] replace hardcoded HVAC/chiller constants.
 * 3. [IranClimateZone] never injects a fake elevation into vehicle physics; altitude must come from route DEM/GPS.
 */
object IranAtmosphericEngine {

    private const val SEA_LEVEL_PRESSURE_PA = 101325.0
    private const val SEA_LEVEL_STD_TEMP_K = 288.15
    private const val TEMP_LAPSE_RATE_K_PER_M = 0.0065
    private const val GRAVITY_M_S2 = 9.80665
    private const val MOLAR_MASS_DRY_AIR = 0.0289644
    private const val UNIVERSAL_GAS_CONSTANT = 8.31447
    private const val SPECIFIC_GAS_CONSTANT_DRY_AIR = 287.058
    private const val SPECIFIC_GAS_CONSTANT_WATER_VAPOR = 461.495

    /**
     * Explicit wind direction convention (V13.0 Audit — Item 18).
     *
     * - [METEOROLOGICAL_FROM]: Standard WMO/METAR convention where `0°` means wind blowing FROM North toward South.
     *   When vehicle heading is `0°` (North), a `0°` wind is a direct headwind (`+windSpeed`).
     * - [VECTOR_TO]: Vector flow direction where `0°` means air mass moving TOWARD North.
     *   When vehicle heading is `0°` (North), a `0°` vector wind is a direct tailwind (`-windSpeed`).
     */
    enum class WindDirectionConvention {
        METEOROLOGICAL_FROM,
        VECTOR_TO
    }

    /**
     * Vehicle-specific cabin HVAC and thermal insulation parameters (V13.0 Audit — Item 19).
     */
    data class VehicleThermalProfile(
        val cabinSetpointCelsius: Double = 22.0,
        val hasHeatPump: Boolean = true,
        val heatPumpCopAtMildCold: Double = 2.6,
        val ptcHeaterMaxKw: Double = 5.5,
        val acCompressorMaxKw: Double = 3.5,
        val cabinInsulationCoefficientKwPerK: Double = 0.11,
        val solarGainMaxKw: Double = 0.55,
        val baseElectronicsLoadKw: Double = 0.30
    ) {
        companion object {
            val DEFAULT_HEAT_PUMP_EV = VehicleThermalProfile(hasHeatPump = true)
            val LEGACY_PTC_ONLY_EV = VehicleThermalProfile(hasHeatPump = false, heatPumpCopAtMildCold = 1.0)
        }
    }

    /**
     * Vehicle-specific battery chemistry and thermal conditioning profile (V13.0 Audit — Item 20).
     */
    enum class BatteryChemistry {
        NMC,
        LFP,
        NCA
    }

    data class VehicleBatteryProfile(
        val chemistry: BatteryChemistry = BatteryChemistry.NMC,
        val coldHeatingThresholdCelsius: Double = if (chemistry == BatteryChemistry.LFP) 5.0 else 0.0,
        val hotCoolingThresholdCelsius: Double = 35.0,
        val maxBatteryHeaterKw: Double = 3.2,
        val maxBatteryChillerKw: Double = 2.4,
        val coldCapacityLossSlopePerDegree: Double = if (chemistry == BatteryChemistry.LFP) 0.0085 else 0.0065
    ) {
        companion object {
            val DEFAULT_NMC = VehicleBatteryProfile(chemistry = BatteryChemistry.NMC)
            val DEFAULT_LFP = VehicleBatteryProfile(chemistry = BatteryChemistry.LFP)
        }
    }

    /**
     * Distinct climatic zones across the Iranian plateau and coastlines.
     * Note: Climate classification is purely meteorological and MUST NEVER be used to fabricate
     * a vehicle's physical elevation when DEM/GPS altitude is unknown (V13.0 Audit — Item 16).
     */
    enum class IranClimateZone(
        val titleFa: String,
        val typicalSummerMaxC: Double,
        val typicalWinterMinC: Double
    ) {
        CASPIAN_HUMID_SUBTROPICAL("سواحل خزری مرطوب (گیلان و مازندران)", 32.0, 4.0),
        ZAGROS_ALBORZ_ALPINE("کوهستانی سردسیر البرز و زاگرس (چالوس، هراز، گردنه حیران)", 26.0, -15.0),
        CENTRAL_PLATEAU_SEMI_ARID("فلات مرکزی نیمه‌خشک (تهران، اصفهان، شیراز، مشهد)", 39.0, -3.0),
        DASHT_E_KAVIR_LUT_HYPER_ARID("کویری و فراخشک (یزد، طبس، کرمان، دشت لوت)", 48.0, 2.0),
        PERSIAN_GULF_HYPER_ARID_HOT("سواحل جنوبی گرم و شرجی (اهواز، بندرعباس، بوشهر، چابهار)", 50.0, 14.0)
    }

    data class AtmosphericState(
        val zone: IranClimateZone,
        val elevationMeters: Double?,
        val isElevationVerified: Boolean,
        val temperatureCelsius: Double,
        val relativeHumidityPercent: Double,
        val barometricPressureHpa: Double,
        val airDensityKgPerM3: Double,
        val aerodynamicDragMultiplier: Double,
        val hvacThermalLoadKw: Double,
        val batteryThermalManagementLoadKw: Double,
        val effectiveUsableCapacityFactor: Double,
        val regenerativeBrakingEfficiencyFactor: Double,
        val headwindComponentKmh: Double,
        val crosswindComponentKmh: Double,
        val totalConsumptionMultiplier: Double,
        val rangeReductionPercent: Double,
        val windConventionUsed: WindDirectionConvention = WindDirectionConvention.METEOROLOGICAL_FROM,
        val advisoriesFa: List<String>
    )

    /**
     * Computes headwind (+ve) and crosswind components under the specified [WindDirectionConvention] (Item 18).
     */
    fun computeWindComponentsKmh(
        windSpeedKmh: Double,
        windDirectionDegrees: Double,
        vehicleHeadingDegrees: Double,
        convention: WindDirectionConvention = WindDirectionConvention.METEOROLOGICAL_FROM
    ): Pair<Double, Double> {
        val safeSpeed = windSpeedKmh.coerceAtLeast(0.0)
        if (safeSpeed == 0.0 || !windDirectionDegrees.isFinite() || !vehicleHeadingDegrees.isFinite()) {
            return 0.0 to 0.0
        }
        // Convert VECTOR_TO into METEOROLOGICAL_FROM by rotating 180 degrees
        val meteorologicalFromDeg = when (convention) {
            WindDirectionConvention.METEOROLOGICAL_FROM -> ((windDirectionDegrees % 360.0) + 360.0) % 360.0
            WindDirectionConvention.VECTOR_TO -> (((windDirectionDegrees + 180.0) % 360.0) + 360.0) % 360.0
        }
        val normalizedHeading = ((vehicleHeadingDegrees % 360.0) + 360.0) % 360.0
        val relativeRad = Math.toRadians(meteorologicalFromDeg - normalizedHeading)
        val headwind = safeSpeed * cos(relativeRad)
        val crosswind = abs(safeSpeed * sin(relativeRad))
        return headwind to crosswind
    }

    /**
     * Classifies a coordinate in Iran into its climatic zone.
     * Note: If [elevationMeters] is null/unknown, classification uses geographic bounds without
     * fabricating a physical elevation.
     */
    fun classifyClimateZone(latitude: Double, longitude: Double, elevationMeters: Double? = null): IranClimateZone {
        val elev = elevationMeters?.takeIf { it.isFinite() }
        return when {
            latitude in 36.3..38.5 && longitude in 48.8..54.2 && (elev == null || elev < 600.0) ->
                IranClimateZone.CASPIAN_HUMID_SUBTROPICAL
            (elev != null && elev >= 1850.0) ||
                (latitude in 35.8..36.5 && longitude in 51.0..52.8 && (elev == null || elev >= 1400.0)) ||
                (latitude in 37.0..39.5 && longitude in 44.0..48.8 && (elev != null && elev >= 1300.0)) ->
                IranClimateZone.ZAGROS_ALBORZ_ALPINE
            latitude < 28.5 || (latitude < 31.8 && longitude < 50.2 && (elev == null || elev < 300.0)) ->
                IranClimateZone.PERSIAN_GULF_HYPER_ARID_HOT
            latitude in 29.0..34.8 && longitude in 53.5..60.5 && (elev == null || elev < 1400.0) ->
                IranClimateZone.DASHT_E_KAVIR_LUT_HYPER_ARID
            else ->
                IranClimateZone.CENTRAL_PLATEAU_SEMI_ARID
        }
    }

    /**
     * Calculates barometric pressure and humid air density at altitude using ISA + Arden Buck vapor pressure.
     */
    fun calculateAirDensity(
        elevationMeters: Double,
        temperatureCelsius: Double,
        relativeHumidityPercent: Double
    ): Pair<Double, Double> {
        val safeElev = elevationMeters.coerceIn(-50.0, 5670.0)
        val safeTempC = temperatureCelsius.coerceIn(-40.0, 60.0)
        val tempK = safeTempC + 273.15
        val safeRh = relativeHumidityPercent.coerceIn(0.0, 100.0) / 100.0

        val exponent = (GRAVITY_M_S2 * MOLAR_MASS_DRY_AIR) / (UNIVERSAL_GAS_CONSTANT * TEMP_LAPSE_RATE_K_PER_M)
        val pressurePa = SEA_LEVEL_PRESSURE_PA *
            (1.0 - (TEMP_LAPSE_RATE_K_PER_M * safeElev) / SEA_LEVEL_STD_TEMP_K).pow(exponent)

        val saturationVaporPressurePa = 611.21 * exp(
            (18.678 - safeTempC / 234.5) * (safeTempC / (257.14 + safeTempC))
        )
        val vaporPressurePa = safeRh * saturationVaporPressurePa
        val dryAirPressurePa = (pressurePa - vaporPressurePa).coerceAtLeast(10000.0)

        val densityKgM3 = (dryAirPressurePa / (SPECIFIC_GAS_CONSTANT_DRY_AIR * tempK)) +
            (vaporPressurePa / (SPECIFIC_GAS_CONSTANT_WATER_VAPOR * tempK))

        return Pair(pressurePa / 100.0, densityKgM3.coerceIn(0.50, 1.45))
    }

    /**
     * Continuous, piecewise-linear range reduction percentage curve as a function of ambient temperature (°C).
     */
    fun rangeReductionPercent(temperatureCelsius: Double): Double {
        if (!temperatureCelsius.isFinite()) return 10.0
        val t = temperatureCelsius.coerceIn(-25.0, 55.0)
        return when {
            t <= -10.0 -> 32.0
            t <= 0.0 -> 22.0 + (0.0 - t) * (10.0 / 10.0)
            t <= 10.0 -> 12.0 + (10.0 - t) * (10.0 / 10.0)
            t < 15.0 -> 3.0 + (15.0 - t) * (9.0 / 5.0)
            t <= 28.0 -> 3.0
            t <= 40.0 -> 3.0 + (t - 28.0) * (7.0 / 12.0)
            t <= 45.0 -> 10.0 + (t - 40.0) * (10.0 / 5.0)
            else -> 20.0
        }
    }

    /**
     * Computes the full atmospheric state and EV energy impact for a given point and vehicle speed.
     * Note: If [elevationMeters] is null, sea-level reference (`0.0m`) is used for conservative air-density
     * drag calculation and `isElevationVerified = false` is recorded — never a fabricated `1200m` altitude.
     */
    fun evaluateAtmosphericImpact(
        coordinate: GeoCoordinate,
        elevationMeters: Double?,
        temperatureCelsius: Double,
        relativeHumidityPercent: Double,
        windSpeedKmh: Double,
        windDirectionDegrees: Double,
        vehicleHeadingDegrees: Double,
        vehicleSpeedKmh: Double,
        precipitationMmPerHour: Double = 0.0,
        solarIrradianceWm2: Double = 600.0,
        windConvention: WindDirectionConvention = WindDirectionConvention.METEOROLOGICAL_FROM,
        thermalProfile: VehicleThermalProfile = VehicleThermalProfile.DEFAULT_HEAT_PUMP_EV,
        batteryProfile: VehicleBatteryProfile = VehicleBatteryProfile.DEFAULT_NMC
    ): AtmosphericState {
        val verifiedElev = elevationMeters?.takeIf { it.isFinite() }
        val zone = classifyClimateZone(coordinate.latitude, coordinate.longitude, verifiedElev)
        // Conservative sea-level reference (0.0m) when elevation is unverified; never inject zone default altitude
        val aeroReferenceElevationMeters = verifiedElev ?: 0.0

        val (pressureHpa, airDensity) = calculateAirDensity(
            elevationMeters = aeroReferenceElevationMeters,
            temperatureCelsius = temperatureCelsius,
            relativeHumidityPercent = relativeHumidityPercent
        )

        val (headwindKmh, crosswindKmh) = computeWindComponentsKmh(
            windSpeedKmh = windSpeedKmh,
            windDirectionDegrees = windDirectionDegrees,
            vehicleHeadingDegrees = vehicleHeadingDegrees,
            convention = windConvention
        )

        val safeVehicleSpeed = vehicleSpeedKmh.coerceAtLeast(15.0)
        val effectiveAirspeedKmh = (safeVehicleSpeed + headwindKmh).coerceAtLeast(5.0)
        val crosswindYawPenalty = 1.0 + 0.0018 * crosswindKmh

        val seaLevelStdDensity = 1.225
        val densityRatio = airDensity / seaLevelStdDensity
        val speedRatioSquared = (effectiveAirspeedKmh / safeVehicleSpeed).pow(2)
        val aeroMultiplier = (densityRatio * speedRatioSquared * crosswindYawPenalty).coerceIn(0.55, 2.60)

        val deltaT = temperatureCelsius - thermalProfile.cabinSetpointCelsius
        val hvacKw = when {
            deltaT > 2.0 -> {
                val sensibleLoad = (deltaT * thermalProfile.cabinInsulationCoefficientKwPerK).coerceAtMost(thermalProfile.acCompressorMaxKw)
                val latentHumidityLoad = if (relativeHumidityPercent > 55.0) {
                    ((relativeHumidityPercent - 55.0) / 45.0) * 0.85
                } else 0.0
                val solarLoad = (solarIrradianceWm2.coerceIn(0.0, 1100.0) / 1000.0) * thermalProfile.solarGainMaxKw
                (sensibleLoad + latentHumidityLoad + solarLoad).coerceIn(0.4, thermalProfile.acCompressorMaxKw + 1.2)
            }
            deltaT < -2.0 -> {
                val rawHeatDemandKw = abs(deltaT) * (thermalProfile.cabinInsulationCoefficientKwPerK * 1.35)
                val effectiveCop = when {
                    !thermalProfile.hasHeatPump -> 1.0
                    temperatureCelsius >= 5.0 -> thermalProfile.heatPumpCopAtMildCold
                    temperatureCelsius >= -5.0 -> (thermalProfile.heatPumpCopAtMildCold * 0.68).coerceAtLeast(1.3)
                    else -> 1.15
                }
                (rawHeatDemandKw / effectiveCop).coerceIn(0.5, thermalProfile.ptcHeaterMaxKw)
            }
            else -> thermalProfile.baseElectronicsLoadKw
        }

        val bmsThermalKw = when {
            temperatureCelsius > batteryProfile.hotCoolingThresholdCelsius ->
                ((temperatureCelsius - batteryProfile.hotCoolingThresholdCelsius) * 0.14)
                    .coerceIn(0.3, batteryProfile.maxBatteryChillerKw)
            temperatureCelsius < batteryProfile.coldHeatingThresholdCelsius ->
                (abs(temperatureCelsius - batteryProfile.coldHeatingThresholdCelsius) * 0.16)
                    .coerceIn(0.4, batteryProfile.maxBatteryHeaterKw)
            else -> 0.05
        }

        val usableCapFactor = when {
            temperatureCelsius in 18.0..32.0 -> 1.00
            temperatureCelsius > 32.0 -> (1.0 - (temperatureCelsius - 32.0) * 0.0025).coerceAtLeast(0.92)
            temperatureCelsius >= 0.0 -> 1.0 - (18.0 - temperatureCelsius) * (batteryProfile.coldCapacityLossSlopePerDegree * 0.85)
            else -> (0.86 - abs(temperatureCelsius) * batteryProfile.coldCapacityLossSlopePerDegree).coerceAtLeast(0.62)
        }

        val regenFactor = when {
            temperatureCelsius >= 12.0 -> 0.88
            temperatureCelsius >= 0.0 -> 0.65 + (temperatureCelsius / 12.0) * 0.23
            else -> (0.65 - abs(temperatureCelsius) * 0.025).coerceAtLeast(0.20)
        }

        val wetRoadRollingPenalty = when {
            precipitationMmPerHour <= 0.1 -> 1.0
            precipitationMmPerHour <= 4.0 -> 1.06
            else -> 1.14
        }

        val baseTractionKw = (safeVehicleSpeed / 100.0).pow(1.6) * 16.5
        val adjustedTractionKw = baseTractionKw * (0.42 * wetRoadRollingPenalty + 0.58 * aeroMultiplier)
        val totalPowerKw = adjustedTractionKw + hvacKw + bmsThermalKw
        val nominalClearWeatherKw = baseTractionKw + 0.35
        val totalConsumptionMult = (totalPowerKw / nominalClearWeatherKw).coerceIn(0.75, 2.45)

        val tempRangeRed = rangeReductionPercent(temperatureCelsius)
        val windRangePenalty = if (headwindKmh > 10.0) (headwindKmh - 10.0) * 0.25 else 0.0
        val precipPenalty = if (precipitationMmPerHour > 1.0) min(8.0, precipitationMmPerHour * 1.2) else 0.0
        val combinedRangeReduction = (tempRangeRed + windRangePenalty + precipPenalty).coerceIn(0.0, 55.0)

        val advisories = mutableListOf<String>()
        if (verifiedElev == null) {
            advisories.add("ارتفاع دقیق مسیر نامشخص است؛ چگالی هوا به‌صورت محافظه‌کارانه محاسبه شد.")
        }
        if (temperatureCelsius >= 40.0) {
            advisories.add("گرمای شدید (${temperatureCelsius.toInt()}°C): سیستم خنک‌کننده باتری فعال است.")
        }
        if (temperatureCelsius <= 2.0) {
            advisories.add("هوای سرد (${temperatureCelsius.toInt()}°C): کاهش ظرفیت مؤثر باتری و محدودیت بازیابی انرژی ترمز.")
        }
        if (headwindKmh >= 25.0) {
            advisories.add("باد مخالف شدید (${headwindKmh.toInt()} km/h): افزایش مقاومت آیرودینامیکی.")
        }

        return AtmosphericState(
            zone = zone,
            elevationMeters = verifiedElev,
            isElevationVerified = verifiedElev != null,
            temperatureCelsius = temperatureCelsius,
            relativeHumidityPercent = relativeHumidityPercent,
            barometricPressureHpa = pressureHpa,
            airDensityKgPerM3 = airDensity,
            aerodynamicDragMultiplier = aeroMultiplier,
            hvacThermalLoadKw = hvacKw,
            batteryThermalManagementLoadKw = bmsThermalKw,
            effectiveUsableCapacityFactor = usableCapFactor,
            regenerativeBrakingEfficiencyFactor = regenFactor,
            headwindComponentKmh = headwindKmh,
            crosswindComponentKmh = crosswindKmh,
            totalConsumptionMultiplier = totalConsumptionMult,
            rangeReductionPercent = combinedRangeReduction,
            windConventionUsed = windConvention,
            advisoriesFa = advisories
        )
    }
}
