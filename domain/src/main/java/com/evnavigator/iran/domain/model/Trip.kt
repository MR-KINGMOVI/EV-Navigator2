package com.evnavigator.iran.domain.model

/**
 * Represents a trip made by an electric vehicle.
 *
 * @param id Unique identifier for the trip
 * @param startDate Start date and time of the trip
 * @param endDate End date and time of the trip
 * @param startLocation Starting location
 * @param endLocation Ending location
 * @param route Route taken for the trip
 * @param vehicle Vehicle used for the trip
 * @param totalDistance Total distance traveled
 * @param totalTime Total time elapsed
 * @param energyConsumed Total energy consumed in kWh
 * @param chargingStops List of charging stops made during the trip
 * @param weatherSnapshots Weather conditions encountered during the trip
 * @param routeSegments Route segments that make up the trip
 */
data class Trip(
    val id: String,
    val startDate: Long, // Unix timestamp
    val endDate: Long, // Unix timestamp
    val startLocation: GeoCoordinate,
    val endLocation: GeoCoordinate,
    val route: Route,
    val vehicle: Vehicle,
    val totalDistance: Distance,
    val totalTime: Long, // in seconds
    val energyConsumed: Double, // in kWh
    val chargingStops: List<ChargingStop>,
    val weatherSnapshots: List<WeatherSnapshot>,
    val routeSegments: List<RouteSegment>
)