package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration

/**
 * Represents an estimated time of arrival.
 */
data class EtaEstimate(
    /**
     * The estimated time of arrival.
     */
    val eta: Long, // Unix timestamp

    /**
     * The estimated travel time.
     */
    val travelTime: Duration,

    /**
     * Confidence level in the ETA (0.0 to 1.0).
     */
    val confidence: Double, // 0.0 = no confidence, 1.0 = full confidence

    /**
     * Whether the ETA is based on live traffic.
     */
    val isBasedOnLiveTraffic: Boolean,

    /**
     * Any adjustments made to the ETA (e.g., due to weather).
     */
    val adjustmentReason: String?
)