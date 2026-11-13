package com.evnavigator.iran.domain.intelligence.routing

/**
 * High-level driver optimization preference.
 */
enum class RouteOptimizationObjective {
    BALANCED,
    MINIMUM_TIME,
    MINIMUM_ENERGY,
    MAXIMUM_CONFIDENCE_AND_SAFETY
}

/**
 * Explicit, centralized scoring weights.
 */
data class RouteScoreWeights(
    val timeWeight: Double,
    val energyWeight: Double,
    val chargingWeight: Double,
    val socRiskWeight: Double,
    val uncertaintyWeight: Double
) {
    init {
        val sum = timeWeight + energyWeight + chargingWeight + socRiskWeight + uncertaintyWeight
        require(kotlin.math.abs(sum - 1.0) < 0.001) { "Weights must sum to 1.0, was $sum" }
    }

    companion object {
        val BALANCED = RouteScoreWeights(0.30, 0.25, 0.20, 0.15, 0.10)
        val MINIMUM_TIME = RouteScoreWeights(0.60, 0.10, 0.15, 0.10, 0.05)
        val MINIMUM_ENERGY = RouteScoreWeights(0.15, 0.55, 0.10, 0.15, 0.05)
        val MAXIMUM_SAFETY = RouteScoreWeights(0.15, 0.15, 0.15, 0.30, 0.25)

        fun forObjective(obj: RouteOptimizationObjective): RouteScoreWeights = when (obj) {
            RouteOptimizationObjective.BALANCED -> BALANCED
            RouteOptimizationObjective.MINIMUM_TIME -> MINIMUM_TIME
            RouteOptimizationObjective.MINIMUM_ENERGY -> MINIMUM_ENERGY
            RouteOptimizationObjective.MAXIMUM_CONFIDENCE_AND_SAFETY -> MAXIMUM_SAFETY
        }
    }
}

/**
 * Candidate route evaluated for intelligent scoring.
 */
data class RouteCandidate(
    val routeId: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val predictedEnergyKwh: Double,
    val predictedArrivalSocPercent: Double,
    val chargingStopCount: Int,
    val totalChargingAndDetourMinutes: Double,
    val uncertaintyScore: Double, // 0.0 (certain) to 1.0 (high uncertainty)
    val hasRestrictedOrInaccessibleRoad: Boolean = false,
    val routeName: String = routeId
)

/**
 * Normalized inspectable components of a candidate's score.
 */
data class ScoreComponents(
    val normalizedTime: Double,
    val normalizedEnergy: Double,
    val normalizedCharging: Double,
    val normalizedSocRisk: Double,
    val normalizedUncertainty: Double,
    val penalty: Double,
    val compositeScore: Double // Lower is better
)

/**
 * Final scored and ranked route with explainable rationale.
 */
data class ScoredRoute(
    val candidate: RouteCandidate,
    val components: ScoreComponents,
    val rank: Int,
    val isViable: Boolean,
    val explanationReason: String
)

/**
 * Intelligent multi-objective route scoring engine.
 */
class IntelligentRouteScorer {

    /**
     * Score and rank candidate routes deterministically based on objective and weights.
     */
    fun scoreAndRankRoutes(
        candidates: List<RouteCandidate>,
        objective: RouteOptimizationObjective = RouteOptimizationObjective.BALANCED,
        customWeights: RouteScoreWeights? = null,
        minAllowedArrivalSocPercent: Double = 10.0
    ): List<ScoredRoute> {
        if (candidates.isEmpty()) return emptyList()

        val weights = customWeights ?: RouteScoreWeights.forObjective(objective)

        // Find min and max for normalization across candidate pool
        val minDuration = candidates.minOf { it.durationSeconds }
        val maxDuration = candidates.maxOf { it.durationSeconds }
        val minEnergy = candidates.minOf { it.predictedEnergyKwh }
        val maxEnergy = candidates.maxOf { it.predictedEnergyKwh }
        val minCharging = candidates.minOf { it.totalChargingAndDetourMinutes }
        val maxCharging = candidates.maxOf { it.totalChargingAndDetourMinutes }

        val scored = candidates.map { candidate ->
            val normTime = normalize(candidate.durationSeconds, minDuration, maxDuration)
            val normEnergy = normalize(candidate.predictedEnergyKwh, minEnergy, maxEnergy)
            val normCharging = normalize(candidate.totalChargingAndDetourMinutes, minCharging, maxCharging)

            // SOC risk: penalty increases if arrival SOC is close to or below minimum buffer
            val socBuffer = candidate.predictedArrivalSocPercent - minAllowedArrivalSocPercent
            val normSocRisk = when {
                socBuffer < 0.0 -> 1.0 // below minimum buffer
                socBuffer > 25.0 -> 0.0 // plenty of buffer
                else -> (25.0 - socBuffer) / 25.0
            }

            val normUncertainty = candidate.uncertaintyScore.coerceIn(0.0, 1.0)

            // Massive penalty for illegal, inaccessible, or negative arrival SOC
            var penalty = 0.0
            var isViable = true
            if (candidate.hasRestrictedOrInaccessibleRoad) {
                penalty += 1000.0
                isViable = false
            }
            if (candidate.predictedArrivalSocPercent < 0.0) {
                penalty += 2000.0
                isViable = false
            }

            val compositeScore = (
                weights.timeWeight * normTime +
                weights.energyWeight * normEnergy +
                weights.chargingWeight * normCharging +
                weights.socRiskWeight * normSocRisk +
                weights.uncertaintyWeight * normUncertainty
            ) + penalty

            val explanation = buildExplanation(candidate, objective, isViable)

            ScoredRoute(
                candidate = candidate,
                components = ScoreComponents(
                    normalizedTime = normTime,
                    normalizedEnergy = normEnergy,
                    normalizedCharging = normCharging,
                    normalizedSocRisk = normSocRisk,
                    normalizedUncertainty = normUncertainty,
                    penalty = penalty,
                    compositeScore = compositeScore
                ),
                rank = 0, // set after sorting
                isViable = isViable,
                explanationReason = explanation
            )
        }.sortedBy { it.components.compositeScore }

        return scored.mapIndexed { index, route ->
            route.copy(rank = index + 1)
        }
    }

    private fun normalize(value: Double, minVal: Double, maxVal: Double): Double {
        val span = maxVal - minVal
        if (span <= 1e-6) return 0.0
        return ((value - minVal) / span).coerceIn(0.0, 1.0)
    }

    private fun buildExplanation(
        candidate: RouteCandidate,
        objective: RouteOptimizationObjective,
        isViable: Boolean
    ): String {
        if (!isViable) {
            return if (candidate.hasRestrictedOrInaccessibleRoad) {
                "Route contains restricted or inaccessible roads for your vehicle class."
            } else {
                "Predicted arrival battery level is below zero without additional charging."
            }
        }
        return when (objective) {
            RouteOptimizationObjective.BALANCED ->
                "Optimal balance between travel time (${(candidate.durationSeconds / 60).toInt()} min) and energy efficiency (${String.format("%.1f", candidate.predictedEnergyKwh)} kWh)."
            RouteOptimizationObjective.MINIMUM_TIME ->
                "Fastest route with minimal travel duration (${(candidate.durationSeconds / 60).toInt()} min)."
            RouteOptimizationObjective.MINIMUM_ENERGY ->
                "Most energy-efficient trajectory, saving battery consumption (${String.format("%.1f", candidate.predictedEnergyKwh)} kWh)."
            RouteOptimizationObjective.MAXIMUM_CONFIDENCE_AND_SAFETY ->
                "Safest route maximizing arrival battery reserve (${String.format("%.1f", candidate.predictedArrivalSocPercent)}%) with lowest road uncertainty."
        }
    }
}
