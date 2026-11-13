package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Location
import kotlinx.coroutines.flow.Flow

/**
 * Interface for providing location updates.
 */
interface LocationProvider {
    /**
     * Start location updates.
     */
    fun startLocationUpdates()

    /**
     * Stop location updates.
     */
    fun stopLocationUpdates()

    /**
     * Get a flow of location updates.
     */
    fun getLocationUpdates(): Flow<Location>

    /**
     * Get the current location.
     */
    fun getCurrentLocation(): Location?

    /**
     * Check if location provider is available.
     */
    fun isAvailable(): Boolean
}