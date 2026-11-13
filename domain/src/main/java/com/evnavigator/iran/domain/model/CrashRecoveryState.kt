package com.evnavigator.iran.domain.model

/**
 * Crash-safe recovery state machine for dataset installation and activation.
 * 
 * This state machine tracks the lifecycle of dataset operations across process death,
 * device restart, and application crashes. It ensures that incomplete or corrupted
 * operations are detected and safely rolled back without activating invalid data.
 * 
 * States:
 * - NOT_STARTED: No operation in progress
 * - DOWNLOADING: Package download in progress
 * - STAGED: Package downloaded and staged for verification
 * - VERIFYING: Package integrity and validation in progress
 * - READY: Package verified and ready for installation
 * - INSTALLING: Installation in progress
 * - ACTIVE: Dataset successfully activated and in use
 * - FAILED: Operation failed with error state
 * - ROLLBACK_REQUIRED: Previous dataset needs to be rolled back
 * 
 * @property state Current recovery state
 * @property datasetId Dataset identifier
 * @property version Dataset version
 * @property operationType Type of operation (INSTALL, UPDATE, ROLLBACK)
 * @property timestamp Timestamp of state transition
 * @property error Optional error information if state is FAILED
 * @property previousVersion Previous active version for rollback
 */
data class CrashRecoveryState(
    val state: RecoveryState,
    val datasetId: String,
    val version: String,
    val operationType: OperationType,
    val timestamp: Long = System.currentTimeMillis(),
    val error: String? = null,
    val previousVersion: String? = null
) {
    /**
     * Recovery state enumeration
     */
    enum class RecoveryState {
        NOT_STARTED,
        DOWNLOADING,
        STAGED,
        VERIFYING,
        READY,
        INSTALLING,
        ACTIVE,
        FAILED,
        ROLLBACK_REQUIRED
    }

    /**
     * Operation type enumeration
     */
    enum class OperationType {
        INSTALL,
        UPDATE,
        ROLLBACK
    }

    /**
     * Creates a new NOT_STARTED state
     */
    companion object {
        fun notStarted(datasetId: String, version: String): CrashRecoveryState {
            return CrashRecoveryState(
                state = RecoveryState.NOT_STARTED,
                datasetId = datasetId,
                version = version,
                operationType = OperationType.INSTALL
            )
        }

        /**
         * Creates a DOWNLOADING state
         */
        fun downloading(datasetId: String, version: String): CrashRecoveryState {
            return CrashRecoveryState(
                state = RecoveryState.DOWNLOADING,
                datasetId = datasetId,
                version = version,
                operationType = OperationType.INSTALL
            )
        }

        /**
         * Creates a STAGED state
         */
        fun staged(datasetId: String, version: String): CrashRecoveryState {
            return CrashRecoveryState(
                state = RecoveryState.STAGED,
                datasetId = datasetId,
                version = version,
                operationType = OperationType.INSTALL
            )
        }

        /**
         * Creates a VERIFYING state
         */
        fun verifying(datasetId: String, version: String): CrashRecoveryState {
            return CrashRecoveryState(
                state = RecoveryState.VERIFYING,
                datasetId = datasetId,
                version = version,
                operationType = OperationType.INSTALL
            )
        }

        /**
         * Creates a READY state
         */
        fun ready(datasetId: String, version: String): CrashRecoveryState {
            return CrashRecoveryState(
                state = RecoveryState.READY,
                datasetId = datasetId,
                version = version,
                operationType = OperationType.INSTALL
            )
        }

        /**
         * Creates an INSTALLING state
         */
        fun installing(datasetId: String, version: String): CrashRecoveryState {
            return CrashRecoveryState(
                state = RecoveryState.INSTALLING,
                datasetId = datasetId,
                version = version,
                operationType = OperationType.INSTALL
            )
        }

        /**
         * Creates an ACTIVE state
         */
        fun active(datasetId: String, version: String): CrashRecoveryState {
            return CrashRecoveryState(
                state = RecoveryState.ACTIVE,
                datasetId = datasetId,
                version = version,
                operationType = OperationType.INSTALL
            )
        }

        /**
         * Creates a FAILED state
         */
        fun failed(
            datasetId: String,
            version: String,
            error: String,
            previousVersion: String? = null
        ): CrashRecoveryState {
            return CrashRecoveryState(
                state = RecoveryState.FAILED,
                datasetId = datasetId,
                version = version,
                operationType = OperationType.INSTALL,
                error = error,
                previousVersion = previousVersion
            )
        }

        /**
         * Creates a ROLLBACK_REQUIRED state
         */
        fun rollbackRequired(
            datasetId: String,
            version: String,
            previousVersion: String
        ): CrashRecoveryState {
            return CrashRecoveryState(
                state = RecoveryState.ROLLBACK_REQUIRED,
                datasetId = datasetId,
                version = version,
                operationType = OperationType.ROLLBACK,
                previousVersion = previousVersion
            )
        }
    }

    /**
     * Checks if this state represents an incomplete operation
     */
    fun isIncomplete(): Boolean {
        return state in listOf(
            RecoveryState.DOWNLOADING,
            RecoveryState.STAGED,
            RecoveryState.VERIFYING,
            RecoveryState.READY,
            RecoveryState.INSTALLING
        )
    }

    /**
     * Checks if this state represents a successful operation
     */
    fun isSuccessful(): Boolean {
        return state == RecoveryState.ACTIVE
    }

    /**
     * Checks if this state represents a failed operation
     */
    fun isFailed(): Boolean {
        return state == RecoveryState.FAILED
    }

    /**
     * Checks if this state requires rollback
     */
    fun requiresRollback(): Boolean {
        return state == RecoveryState.ROLLBACK_REQUIRED
    }

    /**
     * Updates the state with a new timestamp
     */
    fun withTimestamp(timestamp: Long): CrashRecoveryState {
        return copy(timestamp = timestamp)
    }

    /**
     * Updates the state with an error
     */
    fun withError(error: String): CrashRecoveryState {
        return copy(error = error)
    }

    /**
     * Updates the state with a previous version
     */
    fun withPreviousVersion(previousVersion: String): CrashRecoveryState {
        return copy(previousVersion = previousVersion)
    }
}
