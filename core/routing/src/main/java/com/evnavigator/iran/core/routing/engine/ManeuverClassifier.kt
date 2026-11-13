package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.model.RealManeuverType
import kotlin.math.abs

/**
 * Deterministic, unit-testable maneuver classifier for both offline graph routing
 * and live road segment transitions.
 *
 * Classifies turn angles, U-turns, roundabouts, motorway ramps (entry/exit),
 * merges, forks, and straight continuations, and computes speed-appropriate
 * voice/visual announcement thresholds.
 */
object ManeuverClassifier {

    data class ClassificationInput(
        val stepIndex: Int,
        val isLastStep: Boolean,
        val prevRoadName: String?,
        val currentRoadName: String,
        val prevRoadClass: String?,
        val currentRoadClass: String,
        val bearingBefore: Double,
        val bearingAfter: Double,
        val isRoundabout: Boolean = false,
        val isRamp: Boolean = false,
        val exitNumber: String? = null,
        val speedLimitKmh: Int = 80
    )

    data class Thresholds(
        val initialWarningMeters: Double,
        val reminderMeters: Double,
        val executionMeters: Double
    )

    data class StageThreshold(
        val stage: com.evnavigator.iran.core.routing.model.ManeuverAnnouncementStage,
        val triggerDistanceMeters: Double
    )

    data class ClassifiedManeuver(
        val type: RealManeuverType,
        val instructionFarsi: String,
        val thresholds: Thresholds
    )

    /**
     * Unified helper for classifying a transition between two road segments.
     */
    fun classifyManeuver(
        incomingBearingDeg: Double,
        outgoingBearingDeg: Double,
        incomingRoadName: String?,
        outgoingRoadName: String,
        incomingSpeedKmh: Int = 80,
        outgoingSpeedKmh: Int = 80,
        incomingRoadClass: String? = if (incomingSpeedKmh >= 90) "MOTORWAY" else "PRIMARY",
        outgoingRoadClass: String = if (outgoingSpeedKmh >= 90) "MOTORWAY" else "PRIMARY",
        stepIndex: Int = 1,
        isLastStep: Boolean = false,
        isRoundabout: Boolean = false,
        isRamp: Boolean = false,
        exitNumber: String? = null
    ): ClassifiedManeuver {
        val type = classify(
            ClassificationInput(
                stepIndex = stepIndex,
                isLastStep = isLastStep,
                prevRoadName = incomingRoadName,
                currentRoadName = outgoingRoadName,
                prevRoadClass = incomingRoadClass,
                currentRoadClass = outgoingRoadClass,
                bearingBefore = incomingBearingDeg,
                bearingAfter = outgoingBearingDeg,
                isRoundabout = isRoundabout,
                isRamp = isRamp,
                exitNumber = exitNumber,
                speedLimitKmh = outgoingSpeedKmh
            )
        )
        val thresholds = computeAnnouncementThresholds(outgoingSpeedKmh, outgoingRoadClass)
        val instruction = buildPersianInstruction(type, outgoingRoadName, exitNumber)
        return ClassifiedManeuver(type = type, instructionFarsi = instruction, thresholds = thresholds)
    }

    /**
     * Normalizes bearing difference into [-180.0, +180.0].
     * Positive = right turn, Negative = left turn.
     */
    fun normalizeBearingDelta(bearingBefore: Double, bearingAfter: Double): Double {
        var delta = (bearingAfter - bearingBefore) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta <= -180.0) delta += 360.0
        return delta
    }

    fun classify(input: ClassificationInput): RealManeuverType {
        if (input.isLastStep) return RealManeuverType.ARRIVE
        if (input.stepIndex == 0) return RealManeuverType.DEPART

        val roadNameLower = input.currentRoadName.lowercase()
        val prevRoadClassUpper = input.prevRoadClass?.uppercase() ?: ""
        val currRoadClassUpper = input.currentRoadClass.uppercase()

        // 1. Roundabout detection by explicit flag or Persian/English naming
        if (input.isRoundabout ||
            roadNameLower.contains("میدان") ||
            roadNameLower.contains("فلکه") ||
            roadNameLower.contains("roundabout")
        ) {
            return RealManeuverType.ROUNDABOUT
        }

        val delta = normalizeBearingDelta(input.bearingBefore, input.bearingAfter)
        val absDelta = abs(delta)

        // 2. U-Turn detection (sharp reversal > 155 degrees)
        if (absDelta >= 155.0 || roadNameLower.contains("دوربرگردان")) {
            return RealManeuverType.UTURN
        }

        // 3. Ramp / Motorway transitions
        val wasHighway = prevRoadClassUpper == "MOTORWAY" || prevRoadClassUpper == "TRUNK"
        val isHighway = currRoadClassUpper == "MOTORWAY" || currRoadClassUpper == "TRUNK"
        val isLinkOrRamp = input.isRamp ||
            currRoadClassUpper.contains("LINK") ||
            roadNameLower.contains("رمپ") ||
            roadNameLower.contains("خروجی") ||
            roadNameLower.contains("ورودی")

        if (roadNameLower.contains("خروجی") || (wasHighway && !isHighway && isLinkOrRamp)) {
            return RealManeuverType.RAMP_EXIT
        }
        if (roadNameLower.contains("ورودی") || (!wasHighway && isHighway && isLinkOrRamp)) {
            return RealManeuverType.RAMP_ENTER
        }
        if (!wasHighway && isHighway && absDelta < 45.0 && input.prevRoadName != null && input.prevRoadName != input.currentRoadName) {
            return RealManeuverType.MERGE
        }

        // 4. Fork detection on high-speed roads with shallow divergence onto a different named road
        if ((wasHighway || isHighway) &&
            input.prevRoadName != null &&
            input.prevRoadName != input.currentRoadName &&
            absDelta in 12.0..32.0
        ) {
            return if (delta > 0) RealManeuverType.FORK_RIGHT else RealManeuverType.FORK_LEFT
        }

        // 5. Angular turn classification
        return when {
            absDelta < 18.0 -> RealManeuverType.CONTINUE
            delta >= 115.0 -> RealManeuverType.SHARP_RIGHT
            delta <= -115.0 -> RealManeuverType.SHARP_LEFT
            delta >= 45.0 -> RealManeuverType.TURN_RIGHT
            delta <= -45.0 -> RealManeuverType.TURN_LEFT
            delta >= 18.0 -> RealManeuverType.SLIGHT_RIGHT
            else -> RealManeuverType.SLIGHT_LEFT
        }
    }

    fun computeAnnouncementThresholds(speedLimitKmh: Int, roadClass: String): Thresholds {
        val isHighway = roadClass.equals("MOTORWAY", ignoreCase = true) ||
            roadClass.equals("TRUNK", ignoreCase = true) ||
            speedLimitKmh >= 90
        return if (isHighway) {
            Thresholds(
                initialWarningMeters = 1500.0,
                reminderMeters = 500.0,
                executionMeters = 120.0
            )
        } else if (speedLimitKmh >= 60) {
            Thresholds(
                initialWarningMeters = 800.0,
                reminderMeters = 250.0,
                executionMeters = 60.0
            )
        } else {
            Thresholds(
                initialWarningMeters = 400.0,
                reminderMeters = 150.0,
                executionMeters = 35.0
            )
        }
    }

    fun computeAnnouncementThresholds(speedLimitKmh: Int): List<StageThreshold> {
        val roadClass = if (speedLimitKmh >= 90) "MOTORWAY" else if (speedLimitKmh >= 60) "PRIMARY" else "SECONDARY"
        val t = computeAnnouncementThresholds(speedLimitKmh, roadClass)
        return listOf(
            StageThreshold(com.evnavigator.iran.core.routing.model.ManeuverAnnouncementStage.INITIAL_WARNING, t.initialWarningMeters),
            StageThreshold(com.evnavigator.iran.core.routing.model.ManeuverAnnouncementStage.REMINDER, t.reminderMeters),
            StageThreshold(com.evnavigator.iran.core.routing.model.ManeuverAnnouncementStage.EXECUTION, t.executionMeters)
        )
    }

    fun buildPersianInstruction(
        type: RealManeuverType,
        roadName: String,
        exitNumber: String? = null
    ): String {
        val target = roadName.ifBlank { "مسیر پیش رو" }
        val exitSuffix = if (!exitNumber.isNullOrBlank()) " (خروجی $exitNumber)" else ""
        return when (type) {
            RealManeuverType.DEPART -> "حرکت در $target را آغاز کنید"
            RealManeuverType.TURN_LEFT -> "به چپ به سمت $target بپیچید"
            RealManeuverType.TURN_RIGHT -> "به راست به سمت $target بپیچید"
            RealManeuverType.SLIGHT_LEFT -> "کمی به چپ در مسیر $target برانید"
            RealManeuverType.SLIGHT_RIGHT -> "کمی به راست در مسیر $target برانید"
            RealManeuverType.SHARP_LEFT -> "پیچ تند به چپ به سمت $target"
            RealManeuverType.SHARP_RIGHT -> "پیچ تند به راست به سمت $target"
            RealManeuverType.UTURN -> "در $target دور بزنید"
            RealManeuverType.ROUNDABOUT -> "وارد میدان شوید و به سمت $target خارج شوید$exitSuffix"
            RealManeuverType.MERGE -> "به بزرگراه/آزادراه $target بپیوندید"
            RealManeuverType.FORK_LEFT -> "در دوراهی به سمت چپ ($target) بروید"
            RealManeuverType.FORK_RIGHT -> "در دوراهی به سمت راست ($target) بروید"
            RealManeuverType.RAMP_ENTER -> "وارد رمپ ورودی به سمت $target شوید"
            RealManeuverType.RAMP_EXIT -> "از خروجی به سمت $target خارج شوید$exitSuffix"
            RealManeuverType.CONTINUE -> "مسیر مستقیم را در $target ادامه دهید"
            RealManeuverType.ARRIVE -> "به مقصد رسیدید"
        }
    }
}
