package com.evnavigator.iran.core.routing.provider

import com.evnavigator.iran.core.routing.model.RoutePreferences
import com.evnavigator.iran.core.routing.model.RoutingError
import com.evnavigator.iran.core.routing.validation.RouteIntegrityValidator
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.CancellationException

/**
 * Orchestrates multiple [RoutingProvider] instances in priority order (V13.3 Sections 11 & 12):
 * 1. Online Primary (OSRM / Valhalla / GraphHopper)
 * 2. Offline Primary (Imported OSM Road Graph)
 * 3. Embedded Terrain Corridor Graph (Fallback Skeleton)
 */
class CompositeRoutingProvider(
    private val providers: List<RoutingProvider> = listOf(
        OsrmRoutingProvider(),
        OfflineRoadGraphRoutingProvider()
    ),
    private val allowSyntheticFallback: Boolean = false
) : RoutingProvider {

    constructor(
        primaryOnlineProvider: RoutingProvider,
        fallbackOfflineProvider: RoutingProvider,
        isOnlineEnabled: Boolean = true
    ) : this(
        providers = if (isOnlineEnabled) {
            listOf(primaryOnlineProvider, fallbackOfflineProvider)
        } else {
            listOf(fallbackOfflineProvider)
        }
    )

    override val providerName: String = "CompositeRoutingOrchestrator"
    override val sourceType: RoutingSourceType = RoutingSourceType.ONLINE_PRIMARY
    override val priority: Int = 0

    override val capabilities: ProviderCapabilities
        get() {
            val active = sortedActiveProviders()
            return ProviderCapabilities(
                providerName = providerName,
                supportsAlternativeRoutes = active.any { it.capabilities.supportsAlternativeRoutes },
                supportsAvoidTolls = active.any { it.capabilities.supportsAvoidTolls },
                supportsAvoidFerries = active.any { it.capabilities.supportsAvoidFerries },
                supportsAvoidTunnels = active.any { it.capabilities.supportsAvoidTunnels },
                supportsAvoidUnpaved = active.any { it.capabilities.supportsAvoidUnpaved },
                supportsShortestOptimization = active.any { it.capabilities.supportsShortestOptimization },
                supportsWaypoints = active.any { it.capabilities.supportsWaypoints },
                supportsElevationProfile = active.any { it.capabilities.supportsElevationProfile }
            )
        }

    private fun sortedActiveProviders(): List<RoutingProvider> {
        return providers
            .filter { allowSyntheticFallback || it.sourceType != RoutingSourceType.SYNTHETIC_FALLBACK }
            .sortedBy { it.priority }
    }

    override suspend fun isAvailable(origin: GeoCoordinate, destination: GeoCoordinate): Boolean {
        return sortedActiveProviders().any { it.isAvailable(origin, destination) }
    }

    override suspend fun calculateRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate>,
        preferences: RoutePreferences
    ): Result<EnrichedRouteResult> {
        RouteIntegrityValidator.validateWaypoints(origin, destination, waypoints)
            .onFailure { return Result.failure(it) }

        val activeProviders = sortedActiveProviders()
        if (activeProviders.isEmpty()) {
            return Result.failure(
                RoutingError.GraphNotAvailable("No active routing providers configured")
            )
        }

        val failureReasons = mutableListOf<String>()
        var primaryFailed = false
        var lastException: Throwable? = null

        for (provider in activeProviders) {
            try {
                if (!provider.isAvailable(origin, destination)) {
                    failureReasons.add("${provider.providerName}: Unavailable")
                    primaryFailed = true
                    continue
                }

                val result = provider.calculateRoute(origin, destination, waypoints, preferences)
                if (result.isSuccess) {
                    val enriched = result.getOrThrow()
                    val updatedProvenance = if (primaryFailed) {
                        val fallbackWarn = "استفاده از مسیر جایگزین آفلاین به دلیل عدم دسترسی به مسیریاب اصلی"
                        enriched.provenance.copy(
                            isFallbackUsed = true,
                            fallbackReason = failureReasons.joinToString(" | "),
                            warnings = (enriched.provenance.warnings + fallbackWarn).distinct()
                        )
                    } else {
                        enriched.provenance
                    }
                    return Result.success(enriched.copy(provenance = updatedProvenance))
                } else {
                    val err = result.exceptionOrNull()
                    if (err is CancellationException) throw err
                    lastException = err
                    failureReasons.add("${provider.providerName}: ${err?.message ?: "Failed"}")
                    primaryFailed = true
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastException = e
                failureReasons.add("${provider.providerName}: ${e.message}")
                primaryFailed = true
            }
        }

        val combinedReason = failureReasons.joinToString(" | ")
        return Result.failure(
            when (lastException) {
                is RoutingError -> lastException
                else -> RoutingError.NoPathFound(
                    origin = origin,
                    destination = destination,
                    reason = "All routing providers failed: $combinedReason"
                )
            }
        )
    }

    override suspend fun calculateAlternativeRoutes(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        preferences: RoutePreferences,
        maxAlternatives: Int
    ): Result<List<EnrichedRouteResult>> {
        RouteIntegrityValidator.validateWaypoints(origin, destination)
            .onFailure { return Result.failure(it) }

        val activeProviders = sortedActiveProviders()
        val failureReasons = mutableListOf<String>()

        for (provider in activeProviders) {
            try {
                if (!provider.isAvailable(origin, destination)) {
                    failureReasons.add("${provider.providerName}: Unavailable")
                    continue
                }
                val result = provider.calculateAlternativeRoutes(origin, destination, preferences, maxAlternatives)
                if (result.isSuccess) {
                    val routes = result.getOrThrow()
                    if (routes.isNotEmpty()) {
                        return Result.success(routes)
                    }
                } else {
                    val err = result.exceptionOrNull()
                    if (err is CancellationException) throw err
                    failureReasons.add("${provider.providerName}: ${err?.message ?: "Failed"}")
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                failureReasons.add("${provider.providerName}: ${e.message}")
            }
        }

        return Result.failure(
            RoutingError.NoPathFound(
                origin = origin,
                destination = destination,
                reason = "No provider could generate alternative routes: ${failureReasons.joinToString(" | ")}"
            )
        )
    }
}
