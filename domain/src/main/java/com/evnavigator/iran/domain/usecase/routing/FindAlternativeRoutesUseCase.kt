package com.evnavigator.iran.domain.usecase.routing

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.RoutePreferences
import com.evnavigator.iran.domain.model.RouteResult
import com.evnavigator.iran.domain.model.routing.RoutingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Use case for finding alternative routes between two points
 */
class FindAlternativeRoutesUseCase(
    private val routingEngine: RoutingEngine
) {
    suspend operator fun invoke(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        @Suppress("UNUSED_PARAMETER") preferences: RoutePreferences = RoutePreferences(),
        maxAlternatives: Int = 3
    ): Result<List<RouteResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val routes = routingEngine.getAlternativeRoutes(origin, destination)
                val results = routes.take(maxAlternatives).map { route ->
                    RouteResult(
                        id = route.id,
                        origin = route.start,
                        destination = route.end,
                        distance = route.distance.meters,
                        estimatedTime = route.estimatedTime.toDouble(),
                        calculatedAt = System.currentTimeMillis()
                    )
                }
                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
