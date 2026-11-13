package com.evnavigator.iran.domain.model

/**
 * Represents a road segment.
 */
data class Road(
    val id: String,
    val name: String?,
    val type: String,
    val classType: RoadClass,
    val access: RoadAccess,
    val speedLimit: Speed, // in km/h
    val gradient: Double, // in percentage, or Double.NaN if elevation is unavailable
    val surface: String,
    val isTollRoad: Boolean,
    val isOneWay: Boolean,
    val isGradientUnknown: Boolean = gradient.isNaN()
)

/**
 * Enum representing different road classes.
 */
enum class RoadClass {
    MOTORWAY,
    PRIMARY,
    SECONDARY,
    TERTIARY,
    RESIDENTIAL,
    SERVICE,
    UNCLASSIFIED,
    TRACK,
    PATH
}

/**
 * Enum representing road access restrictions.
 */
enum class RoadAccess {
    PUBLIC,
    PRIVATE,
    RESTRICTED,
    NO_ENTRY,
    NO_EXIT
}