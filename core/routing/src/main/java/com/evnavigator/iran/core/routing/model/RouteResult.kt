package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.Distance

/**
 * Result of a routing calculation
 */
data class RouteResult(
    val route: Route,
    val alternatives: List<Route> = emptyList(),
    val calculatedAt: Long,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(route.isValid()) { "Main route must be valid" }
    }
    
    val totalDistance: Distance
        get() = route.distance
    
    val estimatedTime: Double
        get() = route.estimatedTime
        
    fun hasAlternatives(): Boolean = alternatives.isNotEmpty()
}