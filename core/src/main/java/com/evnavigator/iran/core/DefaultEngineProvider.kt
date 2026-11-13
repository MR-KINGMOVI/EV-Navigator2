package com.evnavigator.iran.core

import com.evnavigator.iran.core.elevation.ElevationEngine
import com.evnavigator.iran.domain.model.routing.RoutingEngine

/**
 * Default implementation of EngineProvider
 */
class DefaultEngineProvider(
    private val routingEngine: RoutingEngine,
    private val elevationEngine: ElevationEngine
) : EngineProvider {
    
    override fun getRoutingEngine(): RoutingEngine = routingEngine
    
    override fun getElevationEngine(): ElevationEngine = elevationEngine
}