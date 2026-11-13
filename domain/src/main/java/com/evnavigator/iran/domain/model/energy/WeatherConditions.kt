package com.evnavigator.iran.domain.model.energy

import kotlinx.serialization.Serializable

@Serializable
enum class WeatherConditions {
    CLEAR,
    CLOUDY,
    RAINY,
    SNOWY,
    WINDY,
    FOGGY,
    STORMY
}