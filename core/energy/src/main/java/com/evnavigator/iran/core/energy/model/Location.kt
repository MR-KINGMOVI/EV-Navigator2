package com.evnavigator.iran.core.energy.model

import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double? = null
)