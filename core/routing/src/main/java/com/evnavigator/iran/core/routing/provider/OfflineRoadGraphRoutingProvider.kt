package com.evnavigator.iran.core.routing.provider

import com.evnavigator.iran.core.routing.engine.IranRealRoadRoutingEngine
import com.evnavigator.iran.core.routing.importer.OsmDatasetManager
import com.evnavigator.iran.core.routing.model.RoutePreferences
import com.evnavigator.iran.core.routing.model.RoutingError
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline Routing Provider powered by the Iranian Road Graph & OSM Dataset Manager (V13.3 Sections 11 & 12).
 *
 * Strictly distinguishes:
 * - [RoutingSourceType.OFFLINE_PRIMARY] when an imported OSM PBF/XML/EVRN dataset is active.
 * - [RoutingSourceType.EMBEDDED_TERRAIN] when operating on the national intercity corridor skeleton fallback.
 */
class OfflineRoadGraphRoutingProvider(
    private val offlineEngine: IranRealRoadRoutingEngine = IranRealRoadRoutingEngine(enableOnlineRouting = false),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    override val priority: Int = 5
) : RoutingProvider {

    override val providerName: String = "OFFLINE_IRAN_GRAPH"

    override val sourceType: RoutingSourceType
        get() = if (OsmDatasetManager.isUsingImportedOsmDataset()) {
            RoutingSourceType.OFFLINE_PRIMARY
        } else {
            RoutingSourceType.EMBEDDED_TERRAIN
        }

    override val capabilities: ProviderCapabilities
        get() = ProviderCapabilities(
            providerName = providerName,
            supportsAlternativeRoutes = true,
            supportsAvoidTolls = false,
            supportsAvoidFerries = false,
            supportsAvoidTunnels = false,
            supportsAvoidUnpaved = false,
            supportsShortestOptimization = false,
            supportsWaypoints = true,
            supportsElevationProfile = true
        )

    override suspend fun isAvailable(origin: GeoCoordinate, destination: GeoCoordinate): Boolean {
        return validateCoordinates(origin, destination) == null
    }

    override suspend fun calculateRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate>,
        preferences: RoutePreferences
    ): Result<EnrichedRouteResult> = withContext(defaultDispatcher) {
        val validationErr = validateCoordinates(origin, destination)
        if (validationErr != null) return@withContext Result.failure(validationErr)

        try {
            val activeDataset = OsmDatasetManager.getActiveDataset()
            val isFullOsm = OsmDatasetManager.isUsingImportedOsmDataset()
            val currentSourceType = if (isFullOsm) RoutingSourceType.OFFLINE_PRIMARY else RoutingSourceType.EMBEDDED_TERRAIN
            val realRouteResult = offlineEngine.calculateRealRoute(origin, destination)
            if (realRouteResult.polyline.size < 2 || realRouteResult.totalDistanceMeters <= 0.0) {
                return@withContext Result.failure(
                    RoutingError.NoRouteFound(origin.toString(), destination.toString())
                )
            }

            val warningList = if (isFullOsm) {
                listOf("محاسبه‌شده بر اساس دیتاست کامل گراف جاده‌ای آفلاین (${activeDataset.metadata.datasetId})")
            } else {
                listOf("محاسبه‌شده بر اساس شبکه محدود کریدورهای بین‌شهری آفلاین ایران (Fallback Skeleton)")
            }

            val provenance = RouteProvenance(
                providerName = providerName,
                sourceType = currentSourceType,
                graphVersion = activeDataset.metadata.datasetId,
                isFallbackUsed = !isFullOsm,
                fallbackReason = if (!isFullOsm) "FULL_OSM_DATASET_NOT_LOADED_USING_CORRIDOR_SKELETON" else null,
                confidenceScore = if (isFullOsm) 0.90 else 0.68,
                alternativeRouteSupport = if (realRouteResult.alternativeRoutes.isNotEmpty()) {
                    AlternativeRouteSupport.SUPPORTED_AND_RETURNED
                } else {
                    AlternativeRouteSupport.SUPPORTED_NONE_AVAILABLE
                },
                originSnapDistanceMeters = realRouteResult.originSnapDistanceMeters,
                destinationSnapDistanceMeters = realRouteResult.destinationSnapDistanceMeters,
                warnings = warningList
            )

            val alternatives = realRouteResult.alternativeRoutes.map { alt ->
                EnrichedRouteResult(
                    route = alt,
                    provenance = provenance.copy(
                        warnings = listOf("مسیر جایگزین - گراف جاده‌ای آفلاین ایران")
                    )
                )
            }

            Result.success(
                EnrichedRouteResult(
                    route = realRouteResult,
                    provenance = provenance,
                    alternatives = alternatives
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(RoutingError.InternalError("Offline route calculation failed: ${e.message}", e))
        }
    }

    override suspend fun calculateAlternativeRoutes(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        preferences: RoutePreferences,
        maxAlternatives: Int
    ): Result<List<EnrichedRouteResult>> = withContext(defaultDispatcher) {
        val primaryRes = calculateRoute(origin, destination, emptyList(), preferences)
        if (primaryRes.isFailure) {
            return@withContext Result.failure(
                primaryRes.exceptionOrNull() ?: RoutingError.InternalError("Offline alternative route calculation failed")
            )
        }
        val primary = primaryRes.getOrThrow()
        val list = buildList {
            add(primary)
            addAll(primary.alternatives.take((maxAlternatives - 1).coerceAtLeast(0)))
        }
        Result.success(list)
    }

    private fun validateCoordinates(c1: GeoCoordinate, c2: GeoCoordinate): RoutingError.InvalidInput? {
        if (c1.latitude.isNaN() || c1.longitude.isNaN() || c2.latitude.isNaN() || c2.longitude.isNaN()) {
            return RoutingError.InvalidInput("Coordinates cannot be NaN: origin=$c1, dest=$c2")
        }
        if (c1.latitude !in 24.0..40.5 || c2.latitude !in 24.0..40.5 ||
            c1.longitude !in 43.5..64.5 || c2.longitude !in 43.5..64.5
        ) {
            return RoutingError.InvalidInput("Coordinates outside Iran road network coverage: origin=$c1, dest=$c2")
        }
        if (c1.latitude == c2.latitude && c1.longitude == c2.longitude) {
            return RoutingError.InvalidInput("Origin and destination must be distinct coordinates")
        }
        return null
    }
}
