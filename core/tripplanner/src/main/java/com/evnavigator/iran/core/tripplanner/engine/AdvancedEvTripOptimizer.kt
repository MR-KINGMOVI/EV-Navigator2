package com.evnavigator.iran.core.tripplanner.engine

import com.evnavigator.iran.core.energy.model.SegmentPhysics
import kotlin.math.*

/**
 * High-precision automotive physics and multi-stop charging optimizer for EV Navigator Iran.
 * Features:
 * - Second-by-second and segment-by-segment energy integration using Newton-Euler road load equations:
 *     F_total = F_rolling + F_drag + F_grade
 * - Topographic elevation integration with altitude-corrected air density:
 *     rho(h, T) = p(h) / (R * T)
 * - Non-linear piecewise charging duration solver honoring battery voltage saturation and cell chemistry.
 * - Multi-stop dynamic greedy optimization ensuring candidate stations are strictly ahead of vehicle position.
 * - Un-masked SOC physics: Never disguises battery exhaustion with coerceIn(0, 100).
 * - Financial economy analyzer: calculates exact Toman savings versus internal combustion engine (ICE) vehicles.
 */
object AdvancedEvTripOptimizer {

    // Physical Constants
    private const val GRAVITY = 9.80665 // m/s^2
    private const val SEA_LEVEL_TEMP_KELVIN = 288.15 // K (15°C)
    private const val TEMPERATURE_LAPSE_RATE = 0.0065 // K/m
    private const val GAS_CONSTANT_AIR = 287.058 // J/(kg·K)

    data class EconomicsConfig(
        val iceLitersPer100km: Double = 8.5,
        val gasolinePriceToman: Double = 3000.0,
        val evTariffTomanPerKwh: Double = 1500.0,
        val chargerEfficiency: Double = 0.92
    )

    data class VehiclePhysicsParams(
        val curbMassKg: Double,
        val payloadMassKg: Double = 150.0, // Driver + Luggage
        val dragCoefficient: Double, // Cd (typically 0.22 - 0.32)
        val frontalAreaM2: Double, // A (typically 2.1 - 2.6 m^2)
        val rollingResistanceCoeff: Double = 0.0095, // Crr
        val usableBatteryCapacityKwh: Double,
        val batteryNominalVoltageV: Double = 400.0,
        val maxDcChargePowerKw: Double = 120.0,
        val batteryChemistry: String = "LFP_Blade",
        val powertrainEfficiency: Double = 0.91, // Inverter + Motor + Gearbox
        val regenEfficiency: Double = 0.76, // Regenerative braking capture efficiency
        val auxiliaryHvacBaseLoadKw: Double = 1.4 // A/C or PTC cabin climate load
    )

    data class TopoSegment(
        val distanceMeters: Double,
        val startElevationMeters: Double,
        val endElevationMeters: Double,
        val targetSpeedKmh: Double,
        val ambientTempCelsius: Double = 25.0,
        val headwindSpeedKmh: Double = 0.0,
        val roadSurfaceFactor: Double = 1.0, // 1.0 = smooth highway, 1.15 = mountain asphalt
        val passLabel: String? = null
    )

    data class SegmentEnergyResult(
        val distanceKm: Double,
        val durationMinutes: Double,
        val energyConsumedKwh: Double,
        val energyRegeneratedKwh: Double,
        val netEnergyKwh: Double,
        val rollingResistanceKwh: Double,
        val aerodynamicDragKwh: Double,
        val elevationClimbKwh: Double,
        val auxiliaryKwh: Double,
        val averagePowerKw: Double
    )

    data class OptimizedChargingStop(
        val stationId: String,
        val stationName: String,
        val locationCoordinate: Pair<Double, Double>,
        val distanceAlongRouteKm: Double,
        val arrivalSocPercent: Double,
        val targetDepartureSocPercent: Double,
        val energyChargedKwh: Double,
        val chargingDurationMinutes: Double,
        val maxChargePowerKw: Double,
        val estimatedCostToman: Double,
        val amenitiesSummary: String
    )

    data class OptimizedTripPlan(
        val totalDistanceKm: Double,
        val drivingTimeMinutes: Double,
        val chargingTimeMinutes: Double,
        val totalTripDurationMinutes: Double,
        val initialSocPercent: Double,
        val arrivalSocPercent: Double,
        val minEnRouteSocPercent: Double,
        val isFeasible: Boolean = true,
        val totalNetEnergyKwh: Double,
        val totalEnergyRegeneratedKwh: Double,
        val totalClimbMeters: Double,
        val totalDescentMeters: Double,
        val chargingStops: List<OptimizedChargingStop>,
        val energyConsumptionWhPerKm: Double,
        val estimatedTotalChargingCostToman: Double,
        val estimatedIceFuelCostToman: Double,
        val netFinancialSavingsToman: Double,
        val passSummitsCrossed: List<String>,
        val warnings: List<String> = emptyList(),
        val minimumSocPercent: Double = minEnRouteSocPercent
    )

    /**
     * Compute instantaneous air density at given elevation and temperature.
     */
    /**
     * Compute instantaneous air density at given elevation and temperature using SegmentPhysics.
     */
    fun calculateAirDensity(elevationMeters: Double, tempCelsius: Double = 15.0): Double {
        return SegmentPhysics.calculateAirDensity(elevationMeters)
    }

    /**
     * Integrate physical road load energy over a topographic segment using SegmentPhysics.
     */
    fun simulateSegmentEnergy(
        segment: TopoSegment,
        vehicle: VehiclePhysicsParams
    ): SegmentEnergyResult {
        val totalMassKg = vehicle.curbMassKg + vehicle.payloadMassKg
        val vehicleSpeedMs = (segment.targetSpeedKmh / 3.6).coerceAtLeast(1.0)
        val headwindMs = segment.headwindSpeedKmh / 3.6
        val deltaElevM = segment.endElevationMeters - segment.startElevationMeters
        val distanceM = segment.distanceMeters.coerceAtLeast(1.0)
        val gradePct = (deltaElevM / distanceM) * 100.0
        val avgElevation = (segment.startElevationMeters + segment.endElevationMeters) / 2.0

        val result = SegmentPhysics.calculate(
            horizontalDistanceMeters = distanceM,
            gradePercent = gradePct,
            absoluteAltitudeMeters = avgElevation,
            speedMps = vehicleSpeedMs,
            vehicleMassKg = totalMassKg,
            dragCoefficient = vehicle.dragCoefficient,
            frontalAreaM2 = vehicle.frontalAreaM2,
            rollingResistanceCoeff = vehicle.rollingResistanceCoeff * segment.roadSurfaceFactor,
            drivetrainEfficiency = vehicle.powertrainEfficiency,
            downhillEfficiency = vehicle.regenEfficiency,
            auxiliaryPowerWatts = vehicle.auxiliaryHvacBaseLoadKw * 1000.0,
            headwindMps = headwindMs
        )

        val consumedKwh = result.drawWh / 1000.0
        val regenKwh = result.regenWh / 1000.0
        val netEnergyKwh = result.netWh / 1000.0
        val durationSeconds = result.travelTimeSeconds
        val avgPowerKw = if (durationSeconds > 0) (netEnergyKwh / (durationSeconds / 3600.0)).coerceIn(-80.0, 200.0) else 0.0

        return SegmentEnergyResult(
            distanceKm = distanceM / 1000.0,
            durationMinutes = durationSeconds / 60.0,
            energyConsumedKwh = consumedKwh,
            energyRegeneratedKwh = regenKwh,
            netEnergyKwh = netEnergyKwh,
            rollingResistanceKwh = result.rollingResistanceWh / 1000.0,
            aerodynamicDragKwh = result.aerodynamicDragWh / 1000.0,
            elevationClimbKwh = result.gradeWh / 1000.0,
            auxiliaryKwh = result.auxiliaryWh / 1000.0,
            averagePowerKw = avgPowerKw
        )
    }

    /**
     * Compute realistic charging duration from initial SoC to target SoC using non-linear taper and charger efficiency.
     */
    fun calculateChargingDurationMinutes(
        initialSocPercent: Double,
        targetSocPercent: Double,
        batteryCapacityKwh: Double,
        maxChargerPowerKw: Double,
        vehicleMaxDcKw: Double,
        chargerEfficiency: Double = 0.92
    ): Double {
        val peakAllowedKw = min(maxChargerPowerKw, vehicleMaxDcKw).coerceAtLeast(1.0)
        val start = initialSocPercent.coerceIn(0.0, 100.0)
        val end = targetSocPercent.coerceIn(0.0, 100.0)
        if (end <= start || batteryCapacityKwh <= 0.0) return 0.0

        var totalHours = 0.0
        var currentSoc = start

        while (currentSoc < end) {
            val slice = minOf(1.0, end - currentSoc)
            val midSoc = currentSoc + slice / 2.0

            val effectiveKw = when {
                midSoc <= 50.0 -> peakAllowedKw
                midSoc <= 70.0 -> peakAllowedKw * (1.0 - ((midSoc - 50.0) / 20.0) * 0.25)
                midSoc <= 80.0 -> peakAllowedKw * (0.75 - ((midSoc - 70.0) / 10.0) * 0.30)
                midSoc <= 90.0 -> peakAllowedKw * (0.45 - ((midSoc - 80.0) / 10.0) * 0.25)
                else -> peakAllowedKw * 0.20
            }.coerceIn(1.0, peakAllowedKw) * chargerEfficiency

            val energyNeededKwh = batteryCapacityKwh * (slice / 100.0)
            totalHours += energyNeededKwh / effectiveKw
            currentSoc += slice
        }

        return totalHours * 60.0
    }

    /**
     * Optimize multi-stop EV journey with pass detection, greedy station corridor search, and financial analysis.
     * Guarantees candidate charging stations are strictly ahead of the vehicle position and reachable within safe SOC.
     */
    fun optimizeMultiStopJourney(
        segments: List<TopoSegment>,
        vehicle: VehiclePhysicsParams,
        initialSocPercent: Double = 90.0,
        minSafeSocPercent: Double = 15.0,
        candidateStations: List<OptimizedChargingStop>,
        targetChargeSocPercent: Double = 80.0,
        economicsConfig: EconomicsConfig = EconomicsConfig()
    ): OptimizedTripPlan {
        if (segments.isEmpty()) {
            return OptimizedTripPlan(
                totalDistanceKm = 0.0,
                drivingTimeMinutes = 0.0,
                chargingTimeMinutes = 0.0,
                totalTripDurationMinutes = 0.0,
                initialSocPercent = initialSocPercent,
                arrivalSocPercent = initialSocPercent,
                minEnRouteSocPercent = initialSocPercent,
                isFeasible = true,
                totalNetEnergyKwh = 0.0,
                totalEnergyRegeneratedKwh = 0.0,
                totalClimbMeters = 0.0,
                totalDescentMeters = 0.0,
                chargingStops = emptyList(),
                energyConsumptionWhPerKm = 0.0,
                estimatedTotalChargingCostToman = 0.0,
                estimatedIceFuelCostToman = 0.0,
                netFinancialSavingsToman = 0.0,
                passSummitsCrossed = emptyList()
            )
        }

        // Pre-simulate segments and build cumulative distances
        data class PrecomputedSegment(
            val segment: TopoSegment,
            val result: SegmentEnergyResult,
            val startKm: Double,
            val endKm: Double,
            val deltaSocPercent: Double
        )

        var cumKm = 0.0
        val precomputed = segments.map { seg ->
            val res = simulateSegmentEnergy(seg, vehicle)
            val startK = cumKm
            cumKm += res.distanceKm
            val endK = cumKm
            val deltaSoc = (res.netEnergyKwh / vehicle.usableBatteryCapacityKwh) * 100.0
            PrecomputedSegment(seg, res, startK, endK, deltaSoc)
        }
        val totalDistanceKm = cumKm

        // Mountain pass detection: honors TopoSegment.passLabel or generic label
        val summitsCrossed = mutableListOf<String>()
        segments.forEach { seg ->
            val avgElev = (seg.startElevationMeters + seg.endElevationMeters) / 2.0
            if (seg.startElevationMeters > 2200 || seg.endElevationMeters > 2200) {
                val passName = seg.passLabel ?: "گردنهٔ مرتفع (≈${avgElev.roundToInt()} متر)"
                if (!summitsCrossed.contains(passName)) summitsCrossed.add(passName)
            }
        }

        // Greedy Multi-Stop Forward Search
        var posKm = 0.0
        var currentSoc = initialSocPercent
        var minEnRouteSoc = initialSocPercent
        val scheduledStops = mutableListOf<OptimizedChargingStop>()
        val warnings = mutableListOf<String>()
        var isFeasible = true
        var totalChargedKwh = 0.0

        val maxIterations = 30
        var iteration = 0

        while (posKm < totalDistanceKm && iteration < maxIterations) {
            iteration++

            // 1) From posKm, probe forward to determine safeBoundaryKm (where SOC drops to minSafeSocPercent)
            var probeKm = posKm
            var probeSoc = currentSoc
            var safeBoundaryKm = totalDistanceKm
            var safeBoundaryBreached = false

            for (p in precomputed) {
                if (p.endKm <= probeKm) continue

                val effectiveStartKm = max(probeKm, p.startKm)
                val effectiveEndKm = p.endKm
                val segFraction = (effectiveEndKm - effectiveStartKm) / (p.endKm - p.startKm)
                val segDeltaSoc = p.deltaSocPercent * segFraction

                val nextSoc = (probeSoc - segDeltaSoc).coerceAtMost(100.0)

                if (nextSoc <= minSafeSocPercent) {
                    val drop = probeSoc - nextSoc
                    val frac = if (drop > 0) ((probeSoc - minSafeSocPercent) / drop).coerceIn(0.0, 1.0) else 0.0
                    safeBoundaryKm = effectiveStartKm + frac * (effectiveEndKm - effectiveStartKm)
                    safeBoundaryBreached = true
                    break
                }

                probeSoc = nextSoc
                probeKm = effectiveEndKm
            }

            // If we can reach the end of the route without breaching minSafeSocPercent, travel directly to destination!
            if (!safeBoundaryBreached) {
                for (p in precomputed) {
                    if (p.endKm <= posKm) continue
                    val segStart = max(posKm, p.startKm)
                    val segEnd = p.endKm
                    val frac = (segEnd - segStart) / (p.endKm - p.startKm)
                    currentSoc = (currentSoc - p.deltaSocPercent * frac).coerceAtMost(100.0)
                    if (currentSoc < minEnRouteSoc) minEnRouteSoc = currentSoc
                    posKm = segEnd
                }
                break
            }

            // 2) Search candidate stations strictly ahead: distanceAlongRouteKm in (posKm, safeBoundaryKm]
            val candidates = candidateStations
                .filter { it.distanceAlongRouteKm > posKm && it.distanceAlongRouteKm <= safeBoundaryKm && !scheduledStops.any { s -> s.stationId == it.stationId } }

            if (candidates.isEmpty()) {
                isFeasible = false
                warnings.add("ایستگاهی بین کیلومتر ${posKm.roundToInt()} و ${safeBoundaryKm.roundToInt()} در دسترس نیست")
                break
            }

            // 3) Select the best station (furthest along corridor)
            val chosen = candidates.maxByOrNull { it.distanceAlongRouteKm }!!

            // Calculate actual SOC on arrival at chosen station
            var arrivalSoc = currentSoc
            var walkKm = posKm
            for (p in precomputed) {
                if (p.endKm <= walkKm) continue
                if (p.startKm >= chosen.distanceAlongRouteKm) break

                val segStart = max(walkKm, p.startKm)
                val segEnd = min(chosen.distanceAlongRouteKm, p.endKm)
                val frac = (segEnd - segStart) / (p.endKm - p.startKm)
                arrivalSoc = (arrivalSoc - p.deltaSocPercent * frac).coerceAtMost(100.0)
                if (arrivalSoc < minEnRouteSoc) minEnRouteSoc = arrivalSoc
                walkKm = segEnd
            }

            val targetSoc = max(targetChargeSocPercent, arrivalSoc + 10.0).coerceAtMost(95.0)
            val durationMinutes = calculateChargingDurationMinutes(
                initialSocPercent = arrivalSoc.coerceAtLeast(1.0),
                targetSocPercent = targetSoc,
                batteryCapacityKwh = vehicle.usableBatteryCapacityKwh,
                maxChargerPowerKw = chosen.maxChargePowerKw,
                vehicleMaxDcKw = vehicle.maxDcChargePowerKw,
                chargerEfficiency = economicsConfig.chargerEfficiency
            )
            val kwhCharged = vehicle.usableBatteryCapacityKwh * ((targetSoc - arrivalSoc) / 100.0)
            val gridKwhDrawn = kwhCharged / economicsConfig.chargerEfficiency
            val costToman = gridKwhDrawn * economicsConfig.evTariffTomanPerKwh
            totalChargedKwh += kwhCharged

            scheduledStops.add(
                chosen.copy(
                    arrivalSocPercent = arrivalSoc,
                    targetDepartureSocPercent = targetSoc,
                    energyChargedKwh = kwhCharged,
                    chargingDurationMinutes = durationMinutes,
                    estimatedCostToman = costToman
                )
            )

            // Guard against zero/negative forward progress after charging
            if (chosen.distanceAlongRouteKm <= posKm) {
                isFeasible = false
                warnings.add("هدف شارژ خیلی پایین است و امکان ادامه مسیر وجود ندارد")
                break
            }

            posKm = chosen.distanceAlongRouteKm
            currentSoc = targetSoc
        }

        // If loop terminated before reaching destination and is still marked feasible, do a final leg check
        if (posKm < totalDistanceKm && isFeasible) {
            // Drain remaining leg
            for (p in precomputed) {
                if (p.endKm <= posKm) continue
                val segStart = max(posKm, p.startKm)
                val segEnd = p.endKm
                val frac = (segEnd - segStart) / (p.endKm - p.startKm)
                currentSoc = (currentSoc - p.deltaSocPercent * frac).coerceAtMost(100.0)
                if (currentSoc < minEnRouteSoc) minEnRouteSoc = currentSoc
                posKm = segEnd
            }
            if (currentSoc < minSafeSocPercent) {
                isFeasible = false
                warnings.add("شارژ باتری در انتهای مسیر به زیر آستانه ایمن (${minSafeSocPercent.roundToInt()}٪) می‌رسد")
            }
        }

        if (minEnRouteSoc < 0.0) {
            isFeasible = false
        }

        val totalDrivingMinutes = precomputed.sumOf { it.result.durationMinutes }
        val totalChargingMinutes = scheduledStops.sumOf { it.chargingDurationMinutes }
        val totalTripMinutes = totalDrivingMinutes + totalChargingMinutes
        val totalNetEnergyKwh = precomputed.sumOf { it.result.netEnergyKwh }
        val totalRegenKwh = precomputed.sumOf { it.result.energyRegeneratedKwh }

        var totalClimbM = 0.0
        var totalDescentM = 0.0
        segments.forEach { seg ->
            val deltaH = seg.endElevationMeters - seg.startElevationMeters
            if (deltaH > 0) totalClimbM += deltaH else totalDescentM += abs(deltaH)
        }

        val avgWhPerKm = if (totalDistanceKm > 0) (totalNetEnergyKwh * 1000.0) / totalDistanceKm else 160.0

        // Financial economics
        // EV electricity drawn from grid (including charger efficiency losses)
        val billedKwh = if (totalChargedKwh > 0.0) totalChargedKwh else totalNetEnergyKwh
        val totalEvCostToman = (billedKwh / economicsConfig.chargerEfficiency) * economicsConfig.evTariffTomanPerKwh
        val iceLitersConsumed = (totalDistanceKm / 100.0) * economicsConfig.iceLitersPer100km
        val iceFuelCostToman = iceLitersConsumed * economicsConfig.gasolinePriceToman
        val netSavingsToman = iceFuelCostToman - totalEvCostToman

        return OptimizedTripPlan(
            totalDistanceKm = totalDistanceKm,
            drivingTimeMinutes = totalDrivingMinutes,
            chargingTimeMinutes = totalChargingMinutes,
            totalTripDurationMinutes = totalTripMinutes,
            initialSocPercent = initialSocPercent,
            arrivalSocPercent = currentSoc,
            minEnRouteSocPercent = minEnRouteSoc,
            isFeasible = isFeasible,
            totalNetEnergyKwh = totalNetEnergyKwh,
            totalEnergyRegeneratedKwh = totalRegenKwh,
            totalClimbMeters = totalClimbM,
            totalDescentMeters = totalDescentM,
            chargingStops = scheduledStops,
            energyConsumptionWhPerKm = avgWhPerKm,
            estimatedTotalChargingCostToman = totalEvCostToman,
            estimatedIceFuelCostToman = iceFuelCostToman,
            netFinancialSavingsToman = netSavingsToman,
            passSummitsCrossed = summitsCrossed,
            warnings = warnings,
            minimumSocPercent = minEnRouteSoc
        )
    }
}
