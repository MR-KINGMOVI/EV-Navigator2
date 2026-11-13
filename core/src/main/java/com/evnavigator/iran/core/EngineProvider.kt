package com.evnavigator.iran.core

import com.evnavigator.iran.core.elevation.ElevationEngine
import com.evnavigator.iran.domain.model.routing.RoutingEngine

/**
 * Provides access to various engine implementations for EV navigation
 */
interface EngineProvider {
    fun getRoutingEngine(): RoutingEngine
    fun getElevationEngine(): ElevationEngine
}