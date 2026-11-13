package com.evnavigator.iran.core.energy.model

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