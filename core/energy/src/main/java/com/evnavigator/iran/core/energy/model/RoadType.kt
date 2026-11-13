package com.evnavigator.iran.core.energy.model

import kotlinx.serialization.Serializable

@Serializable
enum class RoadType {
    HIGHWAY,
    EXPRESSWAY,
    CITY_ROAD,
    RURAL_ROAD,
    UNPAVED_ROAD,
    TUNNEL,
    BRIDGE
}