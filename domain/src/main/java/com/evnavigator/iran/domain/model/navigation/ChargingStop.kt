package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Charger
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration
import com.evnavigator.iran.domain.model.Soc

/**
 * Represents a charging stop in the navigation route.
 */
data class ChargingStop(
    /**
     * The charger information.
     */
    val charger: Charger,

    /**
     * Distance to this charging stop from current position.
     */
    val distanceToStop: Distance,

    /**
     * Estimated time to reach this charging stop.
     */
    val timeToStop: Duration,

    /**
     * Estimated SOC when arriving at this charging stop.
     */
    val estimatedArrivalSoc: Soc,

    /**
     * The target SOC to charge to at this stop.
     */
    val targetSoc: Soc,

    /**
     * Estimated charging duration at this stop.
     */
    val estimatedChargingDuration: Duration,

    /**
     * Whether this is a mandatory charging stop (required for trip completion).
     */
    val isMandatory: Boolean
)