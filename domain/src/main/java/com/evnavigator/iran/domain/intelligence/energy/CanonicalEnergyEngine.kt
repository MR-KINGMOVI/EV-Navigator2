package com.evnavigator.iran.domain.intelligence.energy

import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.trip.SegmentPhysicsEnergyCalculator
import com.evnavigator.iran.domain.model.catalog.CanonicalConnectorType
import com.evnavigator.iran.domain.model.catalog.DomainChargingPort
import com.evnavigator.iran.domain.model.trip.intelligence.ConnectorCompatibilityStatus
import com.evnavigator.iran.domain.model.units.KilowattHours
import com.evnavigator.iran.domain.model.units.Kilowatts
import com.evnavigator.iran.domain.model.units.Percentage
import com.evnavigator.iran.domain.model.units.Seconds
import com.evnavigator.iran.domain.policy.EnergyCalculationMode
import kotlin.math.max
import kotlin.math.min

/**
 * Canonical Single Source of Truth (SSOT) facade for EV energy consumption, non-linear DC/AC
 * charging curves, and port-level connector electrical compatibility (V13.3 Sections 4, 6 & 7).
 */
object CanonicalEnergyEngine {

    enum class CanonicalConnectorStandard(
        val isDcFast: Boolean,
        val protocolMaxReferenceKw: Double,
        val communicationProtocol: String,
        val canonicalType: CanonicalConnectorType,
        val aliases: Set<String>
    ) {
        CCS2(true, 350.0, "IEC_62196_3_COMBO2_ISO15118", CanonicalConnectorType.CCS2, setOf("CCS2", "CCS_TYPE_2", "CCS_COMBO_2", "COMBO2", "COMBO_2", "IEC_62196_T2_COMBO")),
        CCS1(true, 250.0, "SAE_J1772_COMBO1", CanonicalConnectorType.CCS1, setOf("CCS1", "CCS_TYPE_1", "CCS_COMBO_1", "COMBO1", "COMBO_1")),
        CHADEMO(true, 100.0, "IEC_62196_3_AA_CHADEMO_CAN", CanonicalConnectorType.CHADEMO, setOf("CHADEMO", "CHAdeMO")),
        GB_T_DC(true, 250.0, "GB_T_20234_3_27930", CanonicalConnectorType.GBT_DC, setOf("GB_T_DC", "GBT_DC", "GB/T DC", "GBT-DC", "GB/T_DC", "GBT_FAST")),
        TESLA_NACS(true, 250.0, "SAE_J3400_NACS", CanonicalConnectorType.TESLA_NACS, setOf("TESLA_NACS", "NACS", "TESLA_SUPERCHARGER", "TESLA")),
        TYPE_2_AC(false, 22.0, "IEC_62196_2_TYPE2_PWM", CanonicalConnectorType.TYPE_2_AC, setOf("TYPE_2", "TYPE2", "TYPE_2_AC", "TYPE 2", "MENNEKES", "IEC_62196_T2", "AC_TYPE_2")),
        GB_T_AC(false, 22.0, "GB_T_20234_2_AC", CanonicalConnectorType.GBT_AC, setOf("GB_T_AC", "GBT_AC", "GB/T AC", "GBT-AC", "GB/T_AC")),
        TYPE_1_AC(false, 19.2, "SAE_J1772_AC", CanonicalConnectorType.UNKNOWN, setOf("TYPE_1", "TYPE1", "TYPE_1_AC", "J1772", "SAE_J1772"));

        companion object {
            fun fromRawName(raw: String?): CanonicalConnectorStandard? {
                if (raw.isNullOrBlank() || CanonicalConnectorType.isAmbiguousRaw(raw)) return null
                val normalized = raw.trim().uppercase()
                return entries.firstOrNull { std ->
                    std.name == normalized || std.aliases.any { it.equals(normalized, ignoreCase = true) }
                }
            }
        }
    }

    data class EffectiveChargingPowerMatch(
        val standard: CanonicalConnectorStandard, val isDcSession: Boolean,
        val stationPortPowerKw: Kilowatts, val vehicleLimitPowerKw: Kilowatts,
        val effectivePeakPowerKw: Kilowatts, val rawConnectorValue: String = standard.name
    )

    data class PortCompatibilityEvaluation(
        val portId: String, val rawPortConnector: String,
        val normalizedPortStandard: CanonicalConnectorStandard?, val canonicalPortConnector: CanonicalConnectorType,
        val rawVehicleConnectors: List<String>, val normalizedVehicleStandards: Set<CanonicalConnectorStandard>,
        val unrecognizedVehicleConnectors: List<String>, val compatibilityStatus: ConnectorCompatibilityStatus,
        val portOperationalStatus: String, val isDcSession: Boolean?,
        val stationPortPowerKw: Double?, val vehicleAcceptanceLimitKw: Double?, val effectivePeakPowerKw: Double?,
        val canProduceDeterministicDuration: Boolean, val canRecommendDeterministicallyFeasible: Boolean,
        val reasonCode: String, val uncertaintyFraction: Double
    ) {
        val isCompatible: Boolean get() = compatibilityStatus == ConnectorCompatibilityStatus.COMPATIBLE && effectivePeakPowerKw != null
    }

    fun evaluatePortCompatibility(
        portConnectorRaw: String?, portRatedPowerKw: Double?, isPortDc: Boolean, isPortAc: Boolean,
        vehicle: VehiclePhysicsParams, cableMaxPowerKw: Double? = null
    ): PortCompatibilityEvaluation = ConnectorCompatibilityEngine.evaluatePortCompatibility(
        vehicle.supportedConnectors, portConnectorRaw, portRatedPowerKw,
        if (isPortDc) true else if (isPortAc) false else null,
        vehicle.dcMaxChargePowerKw, vehicle.acMaxChargePowerKw, cableMaxKw = cableMaxPowerKw
    )

    object ConnectorCompatibilityEngine {
        fun evaluatePortCompatibility(
            supportedVehicleConnectors: List<String>, rawPortConnector: String?, portMaxPowerKw: Double?,
            portIsDc: Boolean? = null, vehicleDcMaxKw: Double?, vehicleAcMaxKw: Double?,
            portId: String = "port_0", portOperationalStatus: String = "UNKNOWN", cableMaxKw: Double? = null
        ): PortCompatibilityEvaluation {
            val cleanPortRaw = rawPortConnector?.trim().orEmpty()
            val portStandard = CanonicalConnectorStandard.fromRawName(cleanPortRaw)
            val canonType = portStandard?.canonicalType ?: CanonicalConnectorType.fromRaw(cleanPortRaw)
            val normalizedVehicle = LinkedHashSet<CanonicalConnectorStandard>()
            val unrecognizedVehicle = mutableListOf<String>()

            for (rawVeh in supportedVehicleConnectors) {
                val trimmed = rawVeh.trim()
                if (trimmed.isEmpty()) continue
                val std = CanonicalConnectorStandard.fromRawName(trimmed)
                if (std != null) normalizedVehicle.add(std) else unrecognizedVehicle.add(trimmed)
            }

            val statusUpper = portOperationalStatus.trim().uppercase().ifEmpty { "UNKNOWN" }
            val isPortConfirmedAvailable = statusUpper == "AVAILABLE"
            val validPortKw = portMaxPowerKw?.takeIf { it.isFinite() && it > 0.0 }
            val resolvedIsDc = portStandard?.isDcFast ?: portIsDc

            fun incomplete(compat: ConnectorCompatibilityStatus, reason: String, vehKw: Double? = null, uncertainty: Double = 1.0) =
                PortCompatibilityEvaluation(
                    portId, cleanPortRaw, portStandard, canonType, supportedVehicleConnectors, normalizedVehicle,
                    unrecognizedVehicle, compat, statusUpper, resolvedIsDc, validPortKw, vehKw, null, false, false, reason, uncertainty
                )

            if (cleanPortRaw.isEmpty() || portStandard == null) {
                val code = if (CanonicalConnectorType.isAmbiguousRaw(cleanPortRaw)) "AMBIGUOUS_PORT_CONNECTOR" else "UNKNOWN_PORT_CONNECTOR"
                return incomplete(ConnectorCompatibilityStatus.UNKNOWN, code)
            }
            if (portIsDc != null && portIsDc != portStandard.isDcFast) {
                return incomplete(ConnectorCompatibilityStatus.INCOMPATIBLE, "PORT_CURRENT_MODE_MISMATCH")
            }
            if (supportedVehicleConnectors.isEmpty() || normalizedVehicle.isEmpty()) {
                return incomplete(ConnectorCompatibilityStatus.UNKNOWN, "UNKNOWN_VEHICLE_CONNECTOR")
            }
            if (portStandard !in normalizedVehicle) {
                val compat = if (unrecognizedVehicle.isNotEmpty()) ConnectorCompatibilityStatus.UNKNOWN else ConnectorCompatibilityStatus.INCOMPATIBLE
                val reason = if (compat == ConnectorCompatibilityStatus.INCOMPATIBLE) "INCOMPATIBLE_CONNECTOR" else "UNRECOGNIZED_VEHICLE_CONNECTOR_RAW"
                return incomplete(compat, reason)
            }

            val validVehicleKw = if (portStandard.isDcFast) {
                vehicleDcMaxKw?.takeIf { it.isFinite() && it > 0.0 }
            } else {
                vehicleAcMaxKw?.takeIf { it.isFinite() && it > 0.0 }
            }
            val validCableKw = cableMaxKw?.takeIf { it.isFinite() && it > 0.0 }

            if (validPortKw == null || validVehicleKw == null) {
                val missingCode = when {
                    validPortKw == null && validVehicleKw == null -> "UNKNOWN_PORT_AND_VEHICLE_POWER"
                    validPortKw == null -> "UNKNOWN_PORT_POWER"
                    portStandard.isDcFast -> "UNKNOWN_VEHICLE_DC_MAX_POWER"
                    else -> "UNKNOWN_VEHICLE_AC_MAX_POWER"
                }
                return incomplete(ConnectorCompatibilityStatus.COMPATIBLE, missingCode, validVehicleKw, 0.50)
            }

            val effectiveKw = listOfNotNull(validPortKw, validVehicleKw, validCableKw, portStandard.protocolMaxReferenceKw).minOrNull()
                ?: min(validPortKw, validVehicleKw)
            val reasonCode = when {
                statusUpper in setOf("OUT_OF_SERVICE", "UNAVAILABLE", "FAULTED", "OFFLINE", "OCCUPIED", "BUSY", "IN_USE") -> "PORT_UNAVAILABLE_$statusUpper"
                !isPortConfirmedAvailable -> "PORT_STATUS_UNKNOWN"
                else -> "COMPATIBLE_AND_AVAILABLE"
            }

            return PortCompatibilityEvaluation(
                portId, cleanPortRaw, portStandard, canonType, supportedVehicleConnectors, normalizedVehicle,
                unrecognizedVehicle, ConnectorCompatibilityStatus.COMPATIBLE, statusUpper, portStandard.isDcFast,
                validPortKw, validVehicleKw, effectiveKw, true, isPortConfirmedAvailable, reasonCode,
                if (isPortConfirmedAvailable) 0.08 else 0.25
            )
        }

        fun evaluateDomainPort(
            port: DomainChargingPort,
            supportedVehicleConnectors: List<String>,
            vehicleAcMaxKw: Double?,
            vehicleDcMaxKw: Double?
        ): PortCompatibilityEvaluation = evaluatePortCompatibility(
            supportedVehicleConnectors = supportedVehicleConnectors,
            rawPortConnector = port.rawConnectorName.ifBlank { port.connectorType.name },
            portMaxPowerKw = port.powerKw,
            portIsDc = port.isDc,
            vehicleDcMaxKw = vehicleDcMaxKw,
            vehicleAcMaxKw = vehicleAcMaxKw,
            portId = port.id,
            portOperationalStatus = port.status
        )

        fun resolveCompatiblePower(
            rawConnectorName: String,
            stationPortMaxKw: Double,
            vehicleAcMaxKw: Double?,
            vehicleDcMaxKw: Double?,
            supportedVehicleConnectors: List<String> = listOf(rawConnectorName)
        ): EffectiveChargingPowerMatch? {
            val eval = evaluatePortCompatibility(
                supportedVehicleConnectors = supportedVehicleConnectors,
                rawPortConnector = rawConnectorName,
                portMaxPowerKw = stationPortMaxKw,
                portIsDc = null,
                vehicleDcMaxKw = vehicleDcMaxKw,
                vehicleAcMaxKw = vehicleAcMaxKw,
                portOperationalStatus = "AVAILABLE"
            )
            if (eval.compatibilityStatus != ConnectorCompatibilityStatus.COMPATIBLE) return null
            val std = eval.normalizedPortStandard ?: return null
            val portKw = eval.stationPortPowerKw ?: return null
            val vehKw = eval.vehicleAcceptanceLimitKw ?: return null
            val effKw = eval.effectivePeakPowerKw ?: return null
            return EffectiveChargingPowerMatch(std, std.isDcFast, Kilowatts(portKw), Kilowatts(vehKw), Kilowatts(effKw), eval.rawPortConnector)
        }
    }

    object ChargeCurveEngine {
        private data class SocTaperBand(val startSoc: Double, val endSoc: Double, val powerFactor: Double)
        private val DC_TAPER_BANDS = listOf(
            SocTaperBand(0.0, 15.0, 0.78), SocTaperBand(15.0, 55.0, 0.98),
            SocTaperBand(55.0, 75.0, 0.72), SocTaperBand(75.0, 88.0, 0.42), SocTaperBand(88.0, 100.0, 0.18)
        )
        private val AC_ONBOARD_BANDS = listOf(SocTaperBand(0.0, 94.0, 0.98), SocTaperBand(94.0, 100.0, 0.58))

        data class ChargingSessionSimulation(
            val arrivalSoc: Percentage, val targetSoc: Percentage,
            val energyAddedToBattery: KilowattHours, val energyDrawnFromGrid: KilowattHours,
            val chargingDuration: Seconds, val optimisticDuration: Seconds, val pessimisticDuration: Seconds,
            val averagePowerKw: Kilowatts, val peakPowerKw: Kilowatts,
            val coldDeratingFactor: Double, val isDcFast: Boolean, val isDeterministic: Boolean = true
        ) {
            val averageSessionPowerKw: Double get() = averagePowerKw.value
        }

        fun estimateChargingSession(
            batteryCapacityKwh: Double, startSocPercent: Double, targetSocPercent: Double,
            effectivePeakPowerKw: Double, isDcFast: Boolean, ambientTemperatureCelsius: Double? = null
        ): ChargingSessionSimulation = simulateChargingSession(
            batteryCapacityKwh, startSocPercent, targetSocPercent, effectivePeakPowerKw, isDcFast, ambientTemperatureCelsius
        )

        fun simulateChargingSessionOrNull(
            usableBatteryKwh: Double?, arrivalSocPercent: Double, targetSocPercent: Double,
            effectivePeakPowerKw: Double?, isDcFast: Boolean, ambientTemperatureCelsius: Double? = null, isBatteryPreconditioned: Boolean = false
        ): ChargingSessionSimulation? {
            if (usableBatteryKwh == null || !usableBatteryKwh.isFinite() || usableBatteryKwh <= 0.0) return null
            if (effectivePeakPowerKw == null || !effectivePeakPowerKw.isFinite() || effectivePeakPowerKw <= 0.0) return null
            return simulateChargingSession(usableBatteryKwh, arrivalSocPercent, targetSocPercent, effectivePeakPowerKw, isDcFast, ambientTemperatureCelsius, isBatteryPreconditioned)
        }

        fun simulateChargingSession(
            usableBatteryKwh: Double, arrivalSocPercent: Double, targetSocPercent: Double,
            effectivePeakPowerKw: Double, isDcFast: Boolean, ambientTemperatureCelsius: Double? = null, isBatteryPreconditioned: Boolean = false
        ): ChargingSessionSimulation {
            require(usableBatteryKwh.isFinite() && usableBatteryKwh > 0.0) { "usableBatteryKwh ($usableBatteryKwh) must be positive" }
            require(effectivePeakPowerKw.isFinite() && effectivePeakPowerKw > 0.0) { "effectivePeakPowerKw ($effectivePeakPowerKw) must be positive" }
            val startSoc = arrivalSocPercent.coerceIn(0.0, 100.0)
            val endSoc = targetSocPercent.coerceIn(startSoc, 100.0)
            if (endSoc <= startSoc + 0.01) {
                return ChargingSessionSimulation(
                    Percentage(startSoc), Percentage(endSoc), KilowattHours.ZERO, KilowattHours.ZERO,
                    Seconds.ZERO, Seconds.ZERO, Seconds.ZERO, Kilowatts.ZERO, Kilowatts(effectivePeakPowerKw), 1.0, isDcFast, ambientTemperatureCelsius != null
                )
            }
            val coldDerating = computeThermalChargingFactor(ambientTemperatureCelsius, isDcFast, isBatteryPreconditioned)
            val chargingEfficiency = if (isDcFast) 0.93 else 0.89
            val bands = if (isDcFast) DC_TAPER_BANDS else AC_ONBOARD_BANDS
            var totalSeconds = 0.0
            var totalEnergyAddedKwh = 0.0
            for (band in bands) {
                val overlapStart = max(startSoc, band.startSoc)
                val overlapEnd = min(endSoc, band.endSoc)
                if (overlapEnd <= overlapStart) continue
                val bandEnergyKwh = usableBatteryKwh * ((overlapEnd - overlapStart) / 100.0)
                val bandPowerKw = (effectivePeakPowerKw * band.powerFactor * coldDerating).coerceAtLeast(0.5)
                totalEnergyAddedKwh += bandEnergyKwh
                totalSeconds += (bandEnergyKwh / bandPowerKw) * 3_600.0
            }
            val avgKw = if (totalSeconds > 0.0) totalEnergyAddedKwh / (totalSeconds / 3_600.0) else 0.0
            val tempUncertainty = if (ambientTemperatureCelsius == null) 0.18 else 0.08
            return ChargingSessionSimulation(
                Percentage(startSoc), Percentage(endSoc), KilowattHours(totalEnergyAddedKwh), KilowattHours(totalEnergyAddedKwh / chargingEfficiency),
                Seconds(totalSeconds), Seconds(totalSeconds * (1.0 - tempUncertainty * 0.5)), Seconds(totalSeconds * (1.0 + tempUncertainty)),
                Kilowatts(avgKw), Kilowatts(effectivePeakPowerKw * coldDerating), coldDerating, isDcFast, ambientTemperatureCelsius != null
            )
        }

        private fun computeThermalChargingFactor(temp: Double?, isDcFast: Boolean, isPreconditioned: Boolean): Double {
            val t = temp ?: return 1.0
            if (!isDcFast) return if (t < -5.0) 0.92 else 1.0
            if (isPreconditioned) return if (t < 0.0) 0.88 else 1.0
            return when { t < -10.0 -> 0.45; t < 0.0 -> 0.62; t < 10.0 -> 0.80; t <= 38.0 -> 1.00; else -> 0.85 }
        }
    }

    fun calculateEnergy(request: EnergyCalculationRequest): EnergyCalculationResult =
        UnifiedReferenceEnergyCalculator.calculate(request)

    fun predictRouteEnergy(
        route: RouteProfileInput, vehicle: VehiclePhysicsParams,
        conditions: EnvironmentalConditions = EnvironmentalConditions(),
        delegate: EnergyPredictionService = DefaultEnergyPredictionService()
    ): TripEnergyPrediction = delegate.predict(route, vehicle, conditions)

    fun calculateLegEnergy(
        profiles: List<SegmentPhysicsEnergyCalculator.SegmentKineticProfile>,
        legStartMeters: Double, legEndMeters: Double, extraDetourMeters: Double,
        departureSocPercent: Double, batteryCapacityKwh: Double, context: TripIntelligenceContext,
        mode: EnergyCalculationMode = EnergyCalculationMode.ESTIMATE_WITH_WARNING
    ): Result<SegmentPhysicsEnergyCalculator.LegEnergySimulationResult> =
        if (mode == EnergyCalculationMode.STRICT) {
            SegmentPhysicsEnergyCalculator.simulateLegEnergyStrict(profiles, legStartMeters, legEndMeters, extraDetourMeters, departureSocPercent, batteryCapacityKwh, context)
        } else {
            Result.success(SegmentPhysicsEnergyCalculator.simulateLegEnergy(profiles, legStartMeters, legEndMeters, extraDetourMeters, departureSocPercent, batteryCapacityKwh, context, mode))
        }
}
