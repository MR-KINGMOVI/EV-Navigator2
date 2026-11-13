package com.evnavigator.iran.core.routing.provider

import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.model.RealRoadRouteResult
import com.evnavigator.iran.core.routing.model.RealRoadRouteSegment
import com.evnavigator.iran.core.routing.model.Route
import com.evnavigator.iran.core.routing.model.RoutePreferences
import com.evnavigator.iran.core.routing.model.RouteResult
import com.evnavigator.iran.core.routing.model.RouteSegment
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate

/**
 * Source type of a routing calculation (V13.3 Section 12).
 */
enum class RoutingSourceType {
    ONLINE_PRIMARY,      // OSRM / Valhalla / GraphHopper live network
    OFFLINE_PRIMARY,     // Local full-graph engine (Imported OSM PBF / XML / EVRN)
    EMBEDDED_TERRAIN,    // Embedded intercity corridor skeleton (Fallback Only)
    SYNTHETIC_FALLBACK   // Restricted synthetic emergency fallback (never used silently in production)
}

enum class RoutingProviderType {
    OSRM,
    VALHALLA,
    GRAPHHOPPER,
    OFFLINE_ROAD_GRAPH
}

enum class RouteValidationStatus {
    VALIDATED,
    FALLBACK_OFFLINE,
    UNVERIFIED
}

enum class RouteDataFreshness {
    LIVE_ONLINE,
    OFFLINE_STATIC_GRAPH,
    EMBEDDED_CORRIDOR_FALLBACK
}

/**
 * Explicit capability and result status for alternative routes.
 */
enum class AlternativeRouteSupport {
    SUPPORTED_AND_RETURNED,
    SUPPORTED_NONE_AVAILABLE,
    NOT_SUPPORTED_BY_PROVIDER
}

/**
 * Declares the authentic feature capabilities of a [RoutingProvider].
 */
data class ProviderCapabilities(
    val providerName: String,
    val supportsAlternativeRoutes: Boolean,
    val supportsAvoidTolls: Boolean,
    val supportsAvoidFerries: Boolean,
    val supportsAvoidTunnels: Boolean,
    val supportsAvoidUnpaved: Boolean,
    val supportsShortestOptimization: Boolean,
    val supportsWaypoints: Boolean = true,
    val supportsElevationProfile: Boolean = false
)

/**
 * Provenance metadata attached to every routing result so the UI and domain
 * know exactly which provider generated the route, whether it is online/offline,
 * whether it used the full OSM dataset or the limited corridor fallback, and its confidence score.
 */
data class RouteProvenance(
    val providerName: String,
    val sourceType: RoutingSourceType,
    val graphVersion: String = "v13.3",
    val calculatedAtTimestamp: Long = System.currentTimeMillis(),
    val isFallbackUsed: Boolean = false,
    val fallbackReason: String? = null,
    val confidenceScore: Double = 1.0,
    val alternativeRouteSupport: AlternativeRouteSupport = AlternativeRouteSupport.NOT_SUPPORTED_BY_PROVIDER,
    val unsupportedPreferences: List<String> = emptyList(),
    val appliedPreferences: List<String> = emptyList(),
    val originSnapDistanceMeters: Double? = null,
    val destinationSnapDistanceMeters: Double? = null,
    val warnings: List<String> = emptyList()
) {
    constructor(
        providerId: String,
        providerType: RoutingProviderType,
        validationStatus: RouteValidationStatus = RouteValidationStatus.VALIDATED,
        freshness: RouteDataFreshness = RouteDataFreshness.OFFLINE_STATIC_GRAPH,
        isOnline: Boolean = providerType != RoutingProviderType.OFFLINE_ROAD_GRAPH,
        warnings: List<String> = emptyList()
    ) : this(
        providerName = providerId,
        sourceType = if (isOnline) RoutingSourceType.ONLINE_PRIMARY else RoutingSourceType.OFFLINE_PRIMARY,
        graphVersion = freshness.name.lowercase(),
        isFallbackUsed = validationStatus == RouteValidationStatus.FALLBACK_OFFLINE,
        warnings = warnings
    )

    val providerId: String
        get() = providerName

    val providerType: RoutingProviderType
        get() = when {
            sourceType == RoutingSourceType.OFFLINE_PRIMARY || sourceType == RoutingSourceType.EMBEDDED_TERRAIN ->
                RoutingProviderType.OFFLINE_ROAD_GRAPH
            providerName.contains("Valhalla", ignoreCase = true) -> RoutingProviderType.VALHALLA
            providerName.contains("GraphHopper", ignoreCase = true) -> RoutingProviderType.GRAPHHOPPER
            else -> RoutingProviderType.OSRM
        }

    val isOnline: Boolean
        get() = sourceType == RoutingSourceType.ONLINE_PRIMARY

    val validationStatus: RouteValidationStatus
        get() = if (isFallbackUsed) RouteValidationStatus.FALLBACK_OFFLINE else RouteValidationStatus.VALIDATED
}

/**
 * Enriched route result containing both the road geometry/maneuvers and provenance.
 */
data class EnrichedRouteResult(
    val route: RealRoadRouteResult,
    val provenance: RouteProvenance,
    val alternatives: List<EnrichedRouteResult> = emptyList()
) {
    constructor(
        polyline: List<GeoCoordinate>,
        totalDistanceMeters: Double,
        totalDurationSeconds: Double,
        provenance: RouteProvenance,
        maneuvers: List<RealRoadManeuver> = emptyList(),
        routeSummary: String = "مسیر جاده‌ای"
    ) : this(
        route = RealRoadRouteResult(
            polyline = polyline,
            totalDistanceMeters = totalDistanceMeters,
            totalDurationSeconds = totalDurationSeconds,
            maneuvers = maneuvers,
            isOnlineRouting = provenance.isOnline,
            routeSummary = routeSummary
        ),
        provenance = provenance
    )

    val polyline: List<GeoCoordinate>
        get() = route.polyline

    val totalDistanceMeters: Double
        get() = route.totalDistanceMeters

    val totalDurationSeconds: Double
        get() = route.totalDurationSeconds

    val maneuvers: List<RealRoadManeuver>
        get() = route.maneuvers

    val routeSummary: String
        get() = route.routeSummary

    val segments: List<RealRoadRouteSegment>
        get() = route.segments

    fun toRouteResult(): RouteResult {
        val nodeIds = polyline.mapIndexed { idx, pt -> "n_${idx}_${pt.latitude}_${pt.longitude}" }
        val edgeIds = (0 until (nodeIds.size - 1).coerceAtLeast(1)).map { "e_$it" }
        val coreRoute = Route(
            id = route.routeId,
            origin = nodeIds.firstOrNull() ?: "origin",
            destination = nodeIds.lastOrNull() ?: "destination",
            nodes = if (nodeIds.size >= 2) nodeIds else listOf("origin", "destination"),
            edges = edgeIds,
            distance = Distance.fromMeters(totalDistanceMeters.coerceAtLeast(1.0)),
            estimatedTime = totalDurationSeconds.coerceAtLeast(1.0)
        )
        val coreSeg = RouteSegment(
            id = "${route.routeId}_seg_0",
            startNode = coreRoute.origin,
            endNode = coreRoute.destination,
            edges = edgeIds,
            distance = coreRoute.distance,
            estimatedTime = coreRoute.estimatedTime
        )
        return RouteResult(
            route = coreRoute,
            segments = listOf(coreSeg),
            totalDistance = coreRoute.distance,
            estimatedTime = coreRoute.estimatedTime,
            metadata = mapOf(
                "provider" to provenance.providerName,
                "sourceType" to provenance.sourceType.name,
                "isFallbackUsed" to provenance.isFallbackUsed
            )
        )
    }
}

typealias RoutingProviderResult = EnrichedRouteResult

/**
 * Unified abstraction for any routing provider (Online OSRM, Valhalla, GraphHopper, Offline Graph, etc.).
 */
interface RoutingProvider {
    val providerName: String
        get() = "RoutingProvider"

    val sourceType: RoutingSourceType
        get() = RoutingSourceType.OFFLINE_PRIMARY

    val priority: Int
        get() = 10

    val capabilities: ProviderCapabilities
        get() = ProviderCapabilities(
            providerName = providerName,
            supportsAlternativeRoutes = false,
            supportsAvoidTolls = false,
            supportsAvoidFerries = false,
            supportsAvoidTunnels = false,
            supportsAvoidUnpaved = false,
            supportsShortestOptimization = false
        )

    suspend fun isAvailable(origin: GeoCoordinate, destination: GeoCoordinate): Boolean = true

    suspend fun calculateRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate> = emptyList(),
        preferences: RoutePreferences = RoutePreferences()
    ): Result<EnrichedRouteResult>

    suspend fun calculateRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        preferences: RoutePreferences
    ): Result<EnrichedRouteResult> = calculateRoute(origin, destination, emptyList(), preferences)

    suspend fun calculateAlternativeRoutes(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        preferences: RoutePreferences = RoutePreferences(),
        maxAlternatives: Int = 3
    ): Result<List<EnrichedRouteResult>>
}
