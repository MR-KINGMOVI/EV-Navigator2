package com.evnavigator.iran.domain.usecase.routing

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.RoutePreferences
import com.evnavigator.iran.domain.model.RouteResult
import com.evnavigator.iran.domain.model.routing.RoutingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Use case for finding the optimal route between two points
 */
class FindOptimalRouteUseCase(
    private val routingEngine: RoutingEngine
) {
    suspend operator fun invoke(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        @Suppress("UNUSED_PARAMETER") preferences: RoutePreferences = RoutePreferences()
    ): Result<RouteResult> {
        return withContext(Dispatchers.IO) {
            try {
                val route = routingEngine.calculateRoute(origin, destination)
                if (route != null) {
                    Result.success(
                        RouteResult(
                            id = route.id,
                            origin = route.start,
                            destination = route.end,
                            distance = route.distance.meters,
                            estimatedTime = route.estimatedTime.toDouble(),
                            calculatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    Result.failure(Exception("Failed to find route"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
