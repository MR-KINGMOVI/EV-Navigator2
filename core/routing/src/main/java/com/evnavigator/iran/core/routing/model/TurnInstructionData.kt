package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.Bearing

/**
 * Data for turn instructions in routing results
 */
data class TurnInstructionData(
    val bearing: Bearing,
    val instruction: String,
    val distanceToNext: Double? = null, // meters
    val estimatedTime: Double? = null   // seconds
)