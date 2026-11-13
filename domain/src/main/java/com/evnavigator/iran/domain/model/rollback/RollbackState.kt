package com.evnavigator.iran.domain.model.rollback

/**
 * Rollback state machine for safe dataset rollback.
 * 
 * This sealed class defines all possible rollback states and transitions.
 * The rollback system ensures that:
 * - Previous known-good dataset is preserved
 * - Rollback is only performed to valid compatible versions
 * - Rollback reason is recorded
 * - Infinite rollback loops are prevented
 * - Failed updates can be recovered
 * 
 * @property state The current rollback state
 * @property reason Human-readable reason for rollback
 * @property previousVersion The version being rolled back from (if applicable)
 * @property targetVersion The version being rolled back to (if applicable)
 * @property timestamp When the state transition occurred
 */
sealed class RollbackState(
    open val state: RollbackStateType,
    open val reason: String,
    open val previousVersion: String? = null,
    open val targetVersion: String? = null,
    open val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Rollback state types.
     */
    enum class RollbackStateType {
        IDLE,                    // No rollback operation in progress
        ROLLBACK_INITIATED,      // Rollback has been requested
        PREPARING_ROLLBACK,      // Preparing rollback operation
        VERIFYING_PREVIOUS,      // Verifying previous version before rollback
        ROLLBACK_IN_PROGRESS,    // Rollback operation in progress
        ROLLBACK_COMPLETE,       // Rollback completed successfully
        ROLLBACK_FAILED,         // Rollback failed
        ROLLBACK_ABORTED,        // Rollback was aborted
        ROLLBACK_REQUIRED,       // Rollback is required due to failure
        ROLLBACK_LOOP_DETECTED   // Infinite rollback loop detected
    }

    /**
     * Idle state - no rollback operation in progress.
     */
    data class Idle(
        override val reason: String = "No rollback operation in progress"
    ) : RollbackState(RollbackStateType.IDLE, reason)

    /**
     * Rollback initiated - rollback has been requested.
     */
    data class RollbackInitiated(
        override val reason: String,
        override val previousVersion: String,
        override val targetVersion: String
    ) : RollbackState(RollbackStateType.ROLLBACK_INITIATED, reason, previousVersion, targetVersion)

    /**
     * Preparing rollback - preparing rollback operation.
     */
    data class PreparingRollback(
        override val reason: String,
        override val previousVersion: String,
        override val targetVersion: String
    ) : RollbackState(RollbackStateType.PREPARING_ROLLBACK, reason, previousVersion, targetVersion)

    /**
     * Verifying previous - verifying previous version before rollback.
     */
    data class VerifyingPrevious(
        override val reason: String,
        override val previousVersion: String,
        override val targetVersion: String
    ) : RollbackState(RollbackStateType.VERIFYING_PREVIOUS, reason, previousVersion, targetVersion)

    /**
     * Rollback in progress - rollback operation in progress.
     */
    data class RollbackInProgress(
        override val reason: String,
        override val previousVersion: String? = null,
        override val targetVersion: String? = null
    ) : RollbackState(RollbackStateType.ROLLBACK_IN_PROGRESS, reason, previousVersion, targetVersion)

    /**
     * Rollback complete - rollback completed successfully.
     */
    data class RollbackComplete(
        override val reason: String,
        override val previousVersion: String,
        override val targetVersion: String
    ) : RollbackState(RollbackStateType.ROLLBACK_COMPLETE, reason, previousVersion, targetVersion)

    /**
     * Rollback failed - rollback failed.
     */
    data class RollbackFailed(
        override val reason: String,
        override val previousVersion: String,
        override val targetVersion: String,
        val error: Throwable? = null
    ) : RollbackState(RollbackStateType.ROLLBACK_FAILED, reason, previousVersion, targetVersion)

    /**
     * Rollback aborted - rollback was aborted.
     */
    data class RollbackAborted(
        override val reason: String,
        override val previousVersion: String,
        override val targetVersion: String
    ) : RollbackState(RollbackStateType.ROLLBACK_ABORTED, reason, previousVersion, targetVersion)

    /**
     * Rollback required - rollback is required due to failure.
     */
    data class RollbackRequired(
        override val reason: String,
        override val previousVersion: String,
        override val targetVersion: String
    ) : RollbackState(RollbackStateType.ROLLBACK_REQUIRED, reason, previousVersion, targetVersion)

    /**
     * Rollback loop detected - infinite rollback loop detected.
     */
    data class RollbackLoopDetected(
        override val reason: String,
        override val previousVersion: String,
        override val targetVersion: String
    ) : RollbackState(RollbackStateType.ROLLBACK_LOOP_DETECTED, reason, previousVersion, targetVersion)

    /**
     * Checks if this state represents an active rollback operation.
     */
    fun isActive(): Boolean {
        return state in listOf(
            RollbackStateType.ROLLBACK_INITIATED,
            RollbackStateType.PREPARING_ROLLBACK,
            RollbackStateType.VERIFYING_PREVIOUS,
            RollbackStateType.ROLLBACK_IN_PROGRESS
        )
    }

    /**
     * Checks if this state indicates a successful rollback.
     */
    fun isSuccessful(): Boolean {
        return state == RollbackStateType.ROLLBACK_COMPLETE
    }

    /**
     * Checks if this state indicates a failed rollback.
     */
    fun isFailed(): Boolean {
        return state == RollbackStateType.ROLLBACK_FAILED
    }

    /**
     * Checks if this state indicates a rollback is required.
     */
    fun isRequired(): Boolean {
        return state == RollbackStateType.ROLLBACK_REQUIRED
    }

    /**
     * Checks if this state indicates an error condition.
     */
    fun isError(): Boolean {
        return state in listOf(
            RollbackStateType.IDLE,
            RollbackStateType.ROLLBACK_FAILED,
            RollbackStateType.ROLLBACK_ABORTED,
            RollbackStateType.ROLLBACK_LOOP_DETECTED
        )
    }

    /**
     * Gets the rollback direction (from version to version).
     */
    fun getRollbackDirection(): Pair<String?, String?> {
        return previousVersion to targetVersion
    }

    /**
     * Converts this state to a serializable map.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "state" to state.name,
            "reason" to reason,
            "previousVersion" to previousVersion,
            "targetVersion" to targetVersion,
            "timestamp" to timestamp
        )
    }

    companion object {
        /**
         * Creates a RollbackState from a map (for deserialization).
         */
        fun fromMap(map: Map<String, Any?>): RollbackState {
            val stateType = RollbackStateType.valueOf(map["state"] as String)
            val reason = (map["reason"] as? String) ?: ""
            val previousVersion = map["previousVersion"] as? String
            val targetVersion = map["targetVersion"] as? String
            val timestamp = (map["timestamp"] as? Number)?.toLong()
                ?: (map["timestamp"] as? String)?.toLongOrNull()
                ?: System.currentTimeMillis()

            return when (stateType) {
                RollbackStateType.IDLE -> Idle(reason)
                RollbackStateType.ROLLBACK_INITIATED -> RollbackInitiated(
                    reason, previousVersion ?: "", targetVersion ?: ""
                )
                RollbackStateType.PREPARING_ROLLBACK -> PreparingRollback(
                    reason, previousVersion ?: "", targetVersion ?: ""
                )
                RollbackStateType.VERIFYING_PREVIOUS -> VerifyingPrevious(
                    reason, previousVersion ?: "", targetVersion ?: ""
                )
                RollbackStateType.ROLLBACK_IN_PROGRESS -> RollbackInProgress(
                    reason, previousVersion, targetVersion
                )
                RollbackStateType.ROLLBACK_COMPLETE -> RollbackComplete(
                    reason, previousVersion ?: "", targetVersion ?: ""
                )
                RollbackStateType.ROLLBACK_FAILED -> RollbackFailed(
                    reason, previousVersion ?: "", targetVersion ?: ""
                )
                RollbackStateType.ROLLBACK_ABORTED -> RollbackAborted(
                    reason, previousVersion ?: "", targetVersion ?: ""
                )
                RollbackStateType.ROLLBACK_REQUIRED -> RollbackRequired(
                    reason, previousVersion ?: "", targetVersion ?: ""
                )
                RollbackStateType.ROLLBACK_LOOP_DETECTED -> RollbackLoopDetected(
                    reason, previousVersion ?: "", targetVersion ?: ""
                )
            }
        }
    }
}
