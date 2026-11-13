package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Location
import com.evnavigator.iran.domain.model.navigation.MatchedLocation

/**
 * Interface for matching GPS locations to road network.
 */
interface MapMatcher {
    /**
     * Match a location to the nearest point on the road network.
     */
    suspend fun matchLocation(location: Location): MatchedLocation?

    /**
     * Match multiple locations to road network.
     */
    suspend fun matchLocations(locations: List<Location>): List<MatchedLocation?>

    /**
     * Get confidence level for a location match.
     */
    fun getMatchConfidence(location: Location, matchedPoint: com.evnavigator.iran.domain.model.Point): Double
}