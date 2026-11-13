package com.evnavigator.iran.core.routing.engine
import kotlinx.coroutines.CancellationException

import com.evnavigator.iran.core.routing.model.*
import com.evnavigator.iran.core.routing.provider.CompositeRoutingProvider
import com.evnavigator.iran.core.routing.provider.RoutingProvider
import com.evnavigator.iran.core.routing.repository.RoutingRepository
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main routing engine coordinating real road pathfinding operations.
 * Connects to pluggable RoutingProviders (OSRM, GraphHopper, Valhalla, Offline Road Graph).
 * Completely eliminates static fake distances and synthetic alternative loops.
 */
class RoutingEngine(
    private val routingRepository: RoutingRepository,
    private val costFunction: CostFunction,
    private val heuristicFunction: HeuristicFunction,
    private val routingProvider: RoutingProvider = CompositeRoutingProvider()
) {
    
    /**
     * Find the optimal real-road route between two points using the authentic RoutingProvider.
     */
    suspend fun findOptimalRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        preferences: RoutePreferences = RoutePreferences(RoutingProfile(TravelMode.ELECTRIC_VEHICLE))
    ): Result<RouteResult> {
        return withContext(Dispatchers.IO) {
            try {
                val providerResult = routingProvider.calculateRoute(origin, destination, preferences)
                if (providerResult.isSuccess) {
                    val res = providerResult.getOrThrow()
                    Result.success(res.toRouteResult())
                } else {
                    Result.failure(providerResult.exceptionOrNull() ?: RoutingError.InternalError("Failed to calculate real road route"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.failure(RoutingError.InternalError(e.message ?: "Unknown routing error"))
            }
        }
    }
    
    /**
     * Find alternative routes between two points.
     * Returns only genuinely calculated routes; never fabricates fake alternative routes with multiplied travel times.
     */
    suspend fun findAlternativeRoutes(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        preferences: RoutePreferences = RoutePreferences(RoutingProfile(TravelMode.ELECTRIC_VEHICLE)),
        maxAlternatives: Int = 3
    ): Result<List<RouteResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val providerResults = routingProvider.calculateAlternativeRoutes(origin, destination, preferences, maxAlternatives)
                if (providerResults.isSuccess) {
                    val list = providerResults.getOrThrow()
                    val routeResults = list.map { it.toRouteResult() }
                    // Strictly deduplicate by geometry nodes
                    val distinctRoutes = routeResults.distinctBy { it.route.nodes }.take(maxAlternatives)
                    Result.success(distinctRoutes)
                } else {
                    // Fallback to single optimal route if alternative query failed
                    val singleRoute = findOptimalRoute(origin, destination, preferences)
                    if (singleRoute.isSuccess) {
                        Result.success(listOf(singleRoute.getOrThrow()))
                    } else {
                        Result.failure(singleRoute.exceptionOrNull() ?: RoutingError.InternalError("Alternative route calculation failed"))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.failure(RoutingError.InternalError(e.message ?: "Failed to generate alternative routes"))
            }
        }
    }
}
