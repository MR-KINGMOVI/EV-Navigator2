package com.evnavigator.iran.domain.model.energy

import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double? = null
)