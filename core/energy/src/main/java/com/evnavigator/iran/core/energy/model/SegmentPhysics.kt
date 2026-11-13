package com.evnavigator.iran.core.energy.model

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class SegmentPhysicsInput(
    val horizontalDistanceM: Double,
    val gradePercent: Double = 0.0,
    val altitudeM: Double? = 0.0,
    val speedMps: Double,
    val windSpeedMps: Double = 0.0,
    val windDirectionDeg: Double? = null,
    val routeHeadingDeg: Double? = null,
    val massKg: Double = 1700.0,
    val dragCoefficient: Double = 0.32,
    val frontalAreaM2: Double = 2.2,
    val rollingResistanceCoeff: Double = 0.012,
    val drivetrainEfficiency: Double = 0.85,
    val regenEfficiency: Double = 0.80,
    val auxPowerW: Double = 200.0
)

data class SegmentPhysicsResult(
    val isValid: Boolean,
    val airDensityKgPerM3: Double,
    val rollingWh: Double,
    val aeroWh: Double,
    val gradeWh: Double,
    val wheelWh: Double,
    val auxWh: Double,
    val drawWh: Double,
    val regenWh: Double,
    val netWh: Double,
    val altitudeAssumed: Boolean = false
)

/**
 * Pure physics engine for electric vehicle segment energy consumption and regeneration.
 * Implements standard longitudinal vehicle dynamics:
 * - Rolling resistance (with incline grade cos-angle component)
 * - Barometric altitude-dependent aerodynamic drag (standard troposphere)
 * - Gravitational potential work on grades
 * - Kinetic acceleration/deceleration transition energy Delta E = 0.5 * m * (v2^2 - v1^2)
 * - Continuous ambient temperature derating & thermodynamic HVAC auxiliary load
 * - Drivetrain efficiency in tractive drive and regeneration efficiency on descents
 * - Guarded against zero/negative speeds and division-by-zero.
 */
object SegmentPhysics {

    const val GRAVITY = 9.80665 // m/s²
    const val SEA_LEVEL_AIR_DENSITY = 1.225 // kg/m³
    const val TEMP_LAPSE_RATE = 0.0065 // K/m
    const val SEA_LEVEL_TEMP_K = 288.15 // K
    const val BAROMETRIC_EXPONENT = 4.25588 // (g * M) / (R * L)

    data class Output(
        val slopeLengthMeters: Double,
        val riseMeters: Double,
        val gradeAngleRadians: Double,
        val airDensityKgPerM3: Double,
        val rollingResistanceForceN: Double,
        val aerodynamicDragForceN: Double,
        val gradeForceN: Double,
        val rollingResistanceWh: Double,
        val aerodynamicDragWh: Double,
        val gradeWh: Double,
        val kineticDeltaWh: Double = 0.0,
        val wheelWh: Double,
        val auxiliaryWh: Double,
        val drawWh: Double,
        val regenWh: Double,
        val netWh: Double,
        val travelTimeSeconds: Double,
        val demUncertaintyWh: Double = 0.0,
        val isGradeAssumed: Boolean = false
    )

    fun calculateAirDensity(altitudeM: Double?): Pair<Double, Boolean> {
        val assumed = altitudeM == null
        val alt = (altitudeM ?: 0.0).coerceIn(-400.0, 6000.0)
        val rho = 1.225 * (1.0 - 0.0065 * alt / 288.15).pow(4.25588)
        return rho.coerceIn(0.65, 1.35) to assumed
    }

    fun compute(input: SegmentPhysicsInput): SegmentPhysicsResult {
        if (input.horizontalDistanceM <= 0.0 || input.speedMps <= 0.0 || input.massKg <= 0.0 ||
            !input.horizontalDistanceM.isFinite() || !input.speedMps.isFinite()
        ) {
            return SegmentPhysicsResult(
                isValid = false,
                airDensityKgPerM3 = 1.225,
                rollingWh = 0.0,
                aeroWh = 0.0,
                gradeWh = 0.0,
                wheelWh = 0.0,
                auxWh = 0.0,
                drawWh = 0.0,
                regenWh = 0.0,
                netWh = 0.0,
                altitudeAssumed = input.altitudeM == null
            )
        }

        val clampedGrade = input.gradePercent.coerceIn(-30.0, 30.0)
        val theta = atan(clampedGrade / 100.0)
        val slopeLen = input.horizontalDistanceM / cos(theta)
        val (rho, altAssumed) = calculateAirDensity(input.altitudeM)

        val headwindMps = if (input.windDirectionDeg != null && input.routeHeadingDeg != null) {
            input.windSpeedMps * cos(Math.toRadians(input.windDirectionDeg - input.routeHeadingDeg))
        } else {
            0.0
        }
        val vAir = input.speedMps + headwindMps

        val fRoll = input.rollingResistanceCoeff * input.massKg * GRAVITY * cos(theta)
        val fAero = 0.5 * rho * input.dragCoefficient * input.frontalAreaM2 * vAir * abs(vAir)
        val fGrade = input.massKg * GRAVITY * sin(theta)

        val rollingWh = (fRoll * slopeLen) / 3600.0
        val aeroWh = (fAero * slopeLen) / 3600.0
        val gradeWh = (fGrade * slopeLen) / 3600.0
        val wheelWh = rollingWh + aeroWh + gradeWh
        val auxWh = input.auxPowerW * (slopeLen / input.speedMps) / 3600.0

        val drawWh: Double
        val regenWh: Double
        if (wheelWh >= 0.0) {
            drawWh = (wheelWh / input.drivetrainEfficiency.coerceIn(0.5, 0.99)) + auxWh
            regenWh = 0.0
        } else {
            drawWh = auxWh
            regenWh = -wheelWh * input.regenEfficiency.coerceIn(0.0, 0.95)
        }
        val netWh = drawWh - regenWh

        return SegmentPhysicsResult(
            isValid = true,
            airDensityKgPerM3 = rho,
            rollingWh = rollingWh,
            aeroWh = aeroWh,
            gradeWh = gradeWh,
            wheelWh = wheelWh,
            auxWh = auxWh,
            drawWh = drawWh,
            regenWh = regenWh,
            netWh = netWh,
            altitudeAssumed = altAssumed
        )
    }

    /**
     * Calculates air density at a given altitude using the standard barometric formula.
     */
    fun calculateAirDensity(altitudeMeters: Double): Double {
        val alt = altitudeMeters.coerceIn(-500.0, 9000.0)
        val tempRatio = (1.0 - (TEMP_LAPSE_RATE * alt) / SEA_LEVEL_TEMP_K).coerceAtLeast(0.01)
        return SEA_LEVEL_AIR_DENSITY * tempRatio.pow(BAROMETRIC_EXPONENT)
    }

    /**
     * Calculates continuous battery usable capacity retention factor as a function of cell temperature.
     * Calibrated for automotive lithium-ion (LFP & NMC) cells across Iranian temperature extremes (-15°C to +50°C).
     */
    fun calculateTemperatureCapacityRetention(tempCelsius: Double): Double {
        return when {
            tempCelsius >= 20.0 && tempCelsius <= 35.0 -> 1.00 // Optimal operating window
            tempCelsius < 20.0 -> {
                // Cold degradation down to -20°C (approx 1% loss per 1.5°C drop)
                (1.0 - (20.0 - tempCelsius) * 0.008).coerceIn(0.65, 1.00)
            }
            else -> {
                // Extreme heat (> 35°C): modest thermal protection derating
                (1.0 - (tempCelsius - 35.0) * 0.004).coerceIn(0.90, 1.00)
            }
        }
    }

    /**
     * Calculates physical cabin HVAC power draw based on ambient vs target temperature delta.
     */
    fun calculateHvacPowerWatts(ambientTempCelsius: Double, targetCabinTempCelsius: Double = 22.0): Double {
        val deltaT = kotlin.math.abs(ambientTempCelsius - targetCabinTempCelsius)
        if (deltaT <= 2.0) return 0.0
        return if (ambientTempCelsius < targetCabinTempCelsius) {
            // Heating mode (heat pump / PTC resistance heating): 120W per degree delta, max 4500W
            (deltaT * 120.0).coerceIn(300.0, 4500.0)
        } else {
            // Cooling mode (A/C compressor): 90W per degree delta, max 3000W
            (deltaT * 90.0).coerceIn(250.0, 3000.0)
        }
    }

    /**
     * Pure calculation of segment energy.
     *
     * @param horizontalDistanceMeters Horizontal distance in meters (from 2D coordinate distanceTo)
     * @param gradePercent Road gradient in percent (e.g. +5.0 for +5% slope). Clamped to [-30.0, 30.0]
     * @param absoluteAltitudeMeters Absolute elevation above sea level in meters (used for air density, default 0.0)
     * @param speedMps Vehicle speed in meters per second (must be > 0 and finite)
     * @param vehicleMassKg Vehicle total operating mass in kilograms
     * @param dragCoefficient Aerodynamic drag coefficient Cd
     * @param frontalAreaM2 Vehicle frontal area in m²
     * @param rollingResistanceCoeff Rolling resistance coefficient Crr
     * @param drivetrainEfficiency Mechanical/electrical efficiency in tractive mode (wheelWh / drivetrainEff, e.g. 0.85)
     * @param downhillEfficiency (regenEff) Wheel-to-battery regenerative efficiency in descent (e.g. 0.80)
     * @param auxiliaryPowerWatts Fixed electrical load in watts (HVAC, computers, lights, e.g. 200 W)
     * @param headwindMps Relative headwind velocity in m/s (positive = headwind, negative = tailwind, default 0.0)
     * @param entrySpeedMps Entry speed for kinetic work delta (defaults to speedMps for steady-state)
     * @param exitSpeedMps Exit speed for kinetic work delta (defaults to speedMps for steady-state)
     */
    fun calculate(
        horizontalDistanceMeters: Double,
        gradePercent: Double,
        absoluteAltitudeMeters: Double = 0.0,
        speedMps: Double,
        vehicleMassKg: Double,
        dragCoefficient: Double,
        frontalAreaM2: Double,
        rollingResistanceCoeff: Double,
        drivetrainEfficiency: Double = 0.85,
        downhillEfficiency: Double = 0.80,
        auxiliaryPowerWatts: Double = 200.0,
        headwindMps: Double = 0.0,
        entrySpeedMps: Double = speedMps,
        exitSpeedMps: Double = speedMps
    ): Output {
        if (speedMps <= 0.0 || speedMps.isNaN() || speedMps.isInfinite()) {
            throw IllegalArgumentException("Speed must be positive and finite, got: $speedMps")
        }
        if (entrySpeedMps < 0.0 || entrySpeedMps.isNaN() || exitSpeedMps < 0.0 || exitSpeedMps.isNaN()) {
            throw IllegalArgumentException("Entry/exit speeds must be non-negative and finite")
        }
        if (horizontalDistanceMeters < 0.0 || horizontalDistanceMeters.isNaN() || horizontalDistanceMeters.isInfinite()) {
            throw IllegalArgumentException("Horizontal distance must be non-negative and finite, got: $horizontalDistanceMeters")
        }

        if (horizontalDistanceMeters == 0.0) {
            val rho = calculateAirDensity(absoluteAltitudeMeters)
            return Output(
                slopeLengthMeters = 0.0,
                riseMeters = 0.0,
                gradeAngleRadians = 0.0,
                airDensityKgPerM3 = rho,
                rollingResistanceForceN = 0.0,
                aerodynamicDragForceN = 0.0,
                gradeForceN = 0.0,
                rollingResistanceWh = 0.0,
                aerodynamicDragWh = 0.0,
                gradeWh = 0.0,
                kineticDeltaWh = 0.0,
                wheelWh = 0.0,
                auxiliaryWh = 0.0,
                drawWh = 0.0,
                regenWh = 0.0,
                netWh = 0.0,
                travelTimeSeconds = 0.0
            )
        }

        val isGradeUnknown = gradePercent.isNaN() || !gradePercent.isFinite()
        val clampedGrade = if (isGradeUnknown) 0.0 else gradePercent.coerceIn(-30.0, 30.0)
        val theta = atan(clampedGrade / 100.0)
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)

        val rise = horizontalDistanceMeters * (clampedGrade / 100.0)
        val slopeLen = horizontalDistanceMeters / cosTheta

        val isAltUnknown = absoluteAltitudeMeters.isNaN() || !absoluteAltitudeMeters.isFinite()
        val rho = calculateAirDensity(if (isAltUnknown) 0.0 else absoluteAltitudeMeters)
        val vAir = (speedMps + headwindMps).coerceAtLeast(0.0)

        val fRoll = rollingResistanceCoeff * vehicleMassKg * GRAVITY * cosTheta
        val fAero = 0.5 * rho * dragCoefficient * frontalAreaM2 * (vAir * vAir)
        val fGrade = vehicleMassKg * GRAVITY * sinTheta

        val rollingWh = (fRoll * slopeLen) / 3600.0
        val aeroWh = (fAero * slopeLen) / 3600.0
        val gradeWh = (fGrade * slopeLen) / 3600.0

        // Kinetic acceleration / deceleration work: Delta E = 0.5 * m * (v2^2 - v1^2)
        val deltaKineticJoules = 0.5 * vehicleMassKg * ((exitSpeedMps * exitSpeedMps) - (entrySpeedMps * entrySpeedMps))
        val kineticDeltaWh = deltaKineticJoules / 3600.0

        val wheelWh = rollingWh + aeroWh + gradeWh + kineticDeltaWh

        val travelTimeSeconds = slopeLen / speedMps
        val auxWh = (auxiliaryPowerWatts * travelTimeSeconds) / 3600.0

        val driveEff = drivetrainEfficiency.coerceIn(0.10, 1.0)
        val regenEff = downhillEfficiency.coerceIn(0.0, 1.0)

        val drawWh: Double
        val regenWh: Double
        if (wheelWh >= 0.0) {
            drawWh = (wheelWh / driveEff) + auxWh
            regenWh = 0.0
        } else {
            drawWh = auxWh
            regenWh = -wheelWh * regenEff
        }
        val netWh = drawWh - regenWh

        val demUncertaintyWh = if (isGradeUnknown) {
            val potentialRiseMeters = (horizontalDistanceMeters * 0.025).coerceAtMost(100.0)
            (vehicleMassKg * GRAVITY * potentialRiseMeters) / (3600.0 * driveEff)
        } else {
            0.0
        }

        return Output(
            slopeLengthMeters = slopeLen,
            riseMeters = rise,
            gradeAngleRadians = theta,
            airDensityKgPerM3 = rho,
            rollingResistanceForceN = fRoll,
            aerodynamicDragForceN = fAero,
            gradeForceN = fGrade,
            rollingResistanceWh = rollingWh,
            aerodynamicDragWh = aeroWh,
            gradeWh = gradeWh,
            kineticDeltaWh = kineticDeltaWh,
            wheelWh = wheelWh,
            auxiliaryWh = auxWh,
            drawWh = drawWh,
            regenWh = regenWh,
            netWh = netWh,
            travelTimeSeconds = travelTimeSeconds,
            demUncertaintyWh = demUncertaintyWh,
            isGradeAssumed = isGradeUnknown
        )
    }
}
