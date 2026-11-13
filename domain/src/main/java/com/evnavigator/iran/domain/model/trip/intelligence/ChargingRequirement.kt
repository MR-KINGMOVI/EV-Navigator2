package com.evnavigator.iran.domain.model.trip.intelligence

import com.evnavigator.iran.domain.model.Soc
import com.evnavigator.iran.domain.model.Trip

/**
 * Indicates whether charging is required for a trip and the conditions.
 */
data class ChargingRequirement(
    val requiresCharging: Boolean,
    val estimatedEnergyDeficitWh: Double? = null, // Energy needed to complete without charging (can be negative)
    val recommendedMinimumReserveSoc: Soc? = null,  // Minimum reserve that should remain after charging
    val reasonsForRequiringCharging: List<String> = emptyList()  // Why it's required or not
)