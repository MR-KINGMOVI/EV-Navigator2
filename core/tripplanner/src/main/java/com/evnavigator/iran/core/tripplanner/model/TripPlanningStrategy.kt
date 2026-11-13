package com.evnavigator.iran.core.tripplanner.model

/**
 * Enum representing different trip planning strategies.
 */
enum class TripPlanningStrategy {
    FASTEST_ARRIVAL,   // minimize total trip time (driving + charging)
    FEWEST_STOPS,      // minimize number of charging stops, charge more per stop
    LOWEST_COST        // minimize total charging cost
}