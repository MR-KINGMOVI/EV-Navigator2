package com.evnavigator.iran.domain.journey.recovery

import com.evnavigator.iran.domain.journey.lifecycle.JourneyLifecycleState

/**
 * Result of evaluating persisted journey for crash recovery.
 */
sealed class RecoveryAssessment {
    data class Recoverable(
        val journeyId: String,
        val originName: String,
        val destinationName: String,
        val lastState: JourneyLifecycleState,
        val lastSocPercent: Double,
        val elapsedMinutesSinceInterruption: Long
    ) : RecoveryAssessment()

    data object NoActiveJourney : RecoveryAssessment()
    data class Expired(val journeyId: String, val reason: String) : RecoveryAssessment()
}

/**
 * Handles validation and restoration of active EV journeys after process recreation or app restart.
 */
class JourneyRecoveryManager(
    private val maxRecoveryWindowMs: Long = 6 * 3600 * 1000L // 6 hours
) {

    /**
     * Assesses whether a saved journey record can be recovered safely.
     */
    fun assessRecovery(
        journeyId: String?,
        stateString: String?,
        originName: String?,
        destinationName: String?,
        lastSocPercent: Double?,
        lastUpdatedEpochMs: Long?,
        currentEpochMs: Long = System.currentTimeMillis()
    ): RecoveryAssessment {
        if (journeyId.isNullOrBlank() || stateString.isNullOrBlank() || lastUpdatedEpochMs == null) {
            return RecoveryAssessment.NoActiveJourney
        }

        val state = try {
            JourneyLifecycleState.valueOf(stateString)
        } catch (e: Exception) {
            return RecoveryAssessment.Expired(journeyId, "Unknown or corrupted lifecycle state '$stateString'")
        }

        if (state.isTerminal) {
            return RecoveryAssessment.NoActiveJourney
        }

        val ageMs = currentEpochMs - lastUpdatedEpochMs
        if (ageMs > maxRecoveryWindowMs) {
            return RecoveryAssessment.Expired(
                journeyId,
                "Journey was last updated ${ageMs / 3600000} hours ago, exceeding maximum recovery window."
            )
        }

        return RecoveryAssessment.Recoverable(
            journeyId = journeyId,
            originName = originName ?: "مبدأ",
            destinationName = destinationName ?: "مقصد",
            lastState = state,
            lastSocPercent = lastSocPercent ?: 50.0,
            elapsedMinutesSinceInterruption = ageMs / 60000L
        )
    }
}
