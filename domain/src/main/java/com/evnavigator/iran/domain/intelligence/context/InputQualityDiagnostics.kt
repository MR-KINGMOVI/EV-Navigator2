package com.evnavigator.iran.domain.intelligence.context

/**
 * Diagnostic issue severity level.
 */
enum class DiagnosticSeverity {
    INFO,
    WARNING,
    CRITICAL_ERROR
}

/**
 * A specific input quality or validation issue.
 */
data class QualityIssue(
    val field: String,
    val severity: DiagnosticSeverity,
    val message: String,
    val isBlocking: Boolean = severity == DiagnosticSeverity.CRITICAL_ERROR
)

/**
 * Detailed diagnostic assessment of a TripIntelligenceContext.
 */
data class InputQualityDiagnostics(
    val isValidForCalculation: Boolean,
    val issues: List<QualityIssue>,
    val explicitAssumptions: List<String>,
    val missingRequiredFields: List<String>,
    val staleFields: List<String>,
    val overallConfidenceScore: Double // 0.0 to 1.0
) {
    val hasBlockingErrors: Boolean get() = issues.any { it.isBlocking }
    val warningCount: Int get() = issues.count { it.severity == DiagnosticSeverity.WARNING }
}

/**
 * Engine for validating trip contexts and producing actionable quality diagnostics.
 */
object InputQualityValidator {

    private const val MAX_WEATHER_STALENESS_MS = 60 * 60 * 1000L // 1 hour
    private const val MAX_TRAFFIC_STALENESS_MS = 15 * 60 * 1000L // 15 mins

    fun validate(
        context: TripIntelligenceContext,
        currentEpochMs: Long = System.currentTimeMillis()
    ): InputQualityDiagnostics {
        val issues = mutableListOf<QualityIssue>()
        val assumptions = mutableListOf<String>()
        val missingFields = mutableListOf<String>()
        val staleFields = mutableListOf<String>()

        // 1. Vehicle ID
        if (context.vehicleId.isBlank()) {
            issues.add(
                QualityIssue("vehicleId", DiagnosticSeverity.CRITICAL_ERROR, "Vehicle ID cannot be blank.")
            )
        }

        // 2. Battery capacity
        val cap = context.batteryCapacityKwh.value
        if (cap == null) {
            missingFields.add("batteryCapacityKwh")
            issues.add(
                QualityIssue("batteryCapacityKwh", DiagnosticSeverity.CRITICAL_ERROR, "Battery capacity is required for energy predictions.")
            )
        } else if (cap <= 0.0 || cap > 300.0) {
            issues.add(
                QualityIssue("batteryCapacityKwh", DiagnosticSeverity.CRITICAL_ERROR, "Battery capacity $cap kWh is outside plausible EV range (0, 300].")
            )
        }
        if (context.batteryCapacityKwh.isAssumption) {
            assumptions.add("Battery capacity assumed: $cap kWh (${context.batteryCapacityKwh.assumptionNote})")
        }

        // 3. Curb weight
        val curb = context.curbWeightKg.value
        if (curb != null && (curb < 400.0 || curb > 6000.0)) {
            issues.add(
                QualityIssue("curbWeightKg", DiagnosticSeverity.WARNING, "Curb weight $curb kg is atypical for passenger EVs.")
            )
        }

        // 4. Payload
        val payload = context.additionalPayloadKg.value
        if (payload != null && payload < 0.0) {
            issues.add(
                QualityIssue("additionalPayloadKg", DiagnosticSeverity.CRITICAL_ERROR, "Payload cannot be negative.")
            )
        }

        // 5. Drag & Frontal area
        val cd = context.dragCoefficient.value
        if (cd != null && (cd < 0.15 || cd > 0.70)) {
            issues.add(
                QualityIssue("dragCoefficient", DiagnosticSeverity.WARNING, "Aerodynamic drag coeff $cd is outside standard automotive range.")
            )
        }
        val fa = context.frontalAreaM2.value
        if (fa != null && (fa < 1.0 || fa > 5.0)) {
            issues.add(
                QualityIssue("frontalAreaM2", DiagnosticSeverity.WARNING, "Frontal area $fa m² is outside typical range.")
            )
        }

        // 6. SOC Validation
        val startSoc = context.currentSocPercent.value
        if (startSoc == null) {
            missingFields.add("currentSocPercent")
            issues.add(
                QualityIssue("currentSocPercent", DiagnosticSeverity.CRITICAL_ERROR, "Current battery SOC is required.")
            )
        } else if (startSoc < 0.0 || startSoc > 100.0) {
            issues.add(
                QualityIssue("currentSocPercent", DiagnosticSeverity.CRITICAL_ERROR, "Current SOC ($startSoc%) must be within [0, 100].")
            )
        }

        val targetSoc = context.targetArrivalSocPercent.value
        if (targetSoc != null && (targetSoc < 0.0 || targetSoc > 100.0)) {
            issues.add(
                QualityIssue("targetArrivalSocPercent", DiagnosticSeverity.CRITICAL_ERROR, "Target arrival SOC ($targetSoc%) must be within [0, 100].")
            )
        }

        val minReserve = context.minReserveSocPercent.value
        if (minReserve != null) {
            if (minReserve < 0.0 || minReserve > 50.0) {
                issues.add(
                    QualityIssue("minReserveSocPercent", DiagnosticSeverity.CRITICAL_ERROR, "Minimum reserve SOC must be between 0% and 50%.")
                )
            }
            if (targetSoc != null && targetSoc < minReserve) {
                issues.add(
                    QualityIssue("targetArrivalSocPercent", DiagnosticSeverity.WARNING, "Target arrival SOC ($targetSoc%) is below configured safety reserve ($minReserve%).")
                )
            }
        }

        // 7. Route Geometry & Distance
        if (context.routeDistanceMeters < 0.0) {
            issues.add(
                QualityIssue("routeDistanceMeters", DiagnosticSeverity.CRITICAL_ERROR, "Route distance cannot be negative.")
            )
        }
        if (context.routeEstimatedDurationSeconds < 0.0) {
            issues.add(
                QualityIssue("routeEstimatedDurationSeconds", DiagnosticSeverity.CRITICAL_ERROR, "Route duration cannot be negative.")
            )
        }

        // 8. Environmental conditions
        val temp = context.ambientTemperatureCelsius.value
        if (temp != null && (temp < -50.0 || temp > 65.0)) {
            issues.add(
                QualityIssue("ambientTemperatureCelsius", DiagnosticSeverity.WARNING, "Ambient temperature $temp°C is extreme.")
            )
        }
        val wind = context.windSpeedMps.value
        if (wind != null && wind < 0.0) {
            issues.add(
                QualityIssue("windSpeedMps", DiagnosticSeverity.CRITICAL_ERROR, "Wind speed cannot be negative.")
            )
        }

        // 9. Freshness checks
        context.weatherFreshnessTimestamp?.let { ts ->
            if (currentEpochMs - ts > MAX_WEATHER_STALENESS_MS) {
                staleFields.add("weather")
                issues.add(
                    QualityIssue("weatherFreshnessTimestamp", DiagnosticSeverity.WARNING, "Weather observation is older than 1 hour.")
                )
            }
        }

        context.trafficFreshnessTimestamp?.let { ts ->
            if (currentEpochMs - ts > MAX_TRAFFIC_STALENESS_MS) {
                staleFields.add("traffic")
                issues.add(
                    QualityIssue("trafficFreshnessTimestamp", DiagnosticSeverity.WARNING, "Traffic observation is older than 15 minutes.")
                )
            }
        }

        // Collect all assumptions
        if (context.curbWeightKg.isAssumption) assumptions.add("Curb weight assumed: ${context.curbWeightKg.value} kg")
        if (context.dragCoefficient.isAssumption) assumptions.add("Drag coefficient assumed: ${context.dragCoefficient.value}")
        if (context.ambientTemperatureCelsius.isAssumption) assumptions.add("Temperature assumed: ${context.ambientTemperatureCelsius.value}°C")
        if (context.hvacPowerKw.isAssumption) assumptions.add("HVAC load assumed: ${context.hvacPowerKw.value} kW")

        // Calculate overall confidence score
        val confidenceWeights = listOf(
            context.batteryCapacityKwh.confidence * 0.25,
            context.currentSocPercent.confidence * 0.25,
            context.curbWeightKg.confidence * 0.15,
            context.dragCoefficient.confidence * 0.15,
            context.ambientTemperatureCelsius.confidence * 0.10,
            (if (staleFields.isEmpty()) 1.0 else 0.5) * 0.10
        )
        val overallScore = confidenceWeights.sum().coerceIn(0.0, 1.0)

        val isValid = issues.none { it.isBlocking }

        return InputQualityDiagnostics(
            isValidForCalculation = isValid,
            issues = issues,
            explicitAssumptions = assumptions,
            missingRequiredFields = missingFields,
            staleFields = staleFields,
            overallConfidenceScore = overallScore
        )
    }
}
