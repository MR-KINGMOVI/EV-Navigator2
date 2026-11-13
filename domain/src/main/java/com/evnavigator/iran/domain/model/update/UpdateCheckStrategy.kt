package com.evnavigator.iran.domain.model.update

/**
 * Strategy for checking for available dataset updates.
 * 
 * Implementations of this interface provide the logic for determining
 * whether an update is available for a specific dataset and what
 * constraints apply.
 * 
 * This abstraction allows different update sources (remote server,
 * local cache, etc.) to be used interchangeably.
 */
interface UpdateCheckStrategy {
    /**
     * Check for available updates for a specific dataset.
     * 
     * @param datasetId The dataset ID to check for updates
     * @param constraints Constraints that must be satisfied for the update to be considered
     * @return UpdateCheckResult indicating whether an update is available and its details
     */
    suspend fun checkForUpdates(
        datasetId: String,
        constraints: UpdateConstraint
    ): UpdateCheckResult
}