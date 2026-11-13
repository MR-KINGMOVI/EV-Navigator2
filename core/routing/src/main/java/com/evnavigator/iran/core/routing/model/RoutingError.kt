package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.GeoCoordinate

/**
 * Sealed hierarchy representing errors that can occur during route calculation (V13.3 Section 1 & 11).
 */
sealed class RoutingError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * No path exists between the specified origin and destination coordinates.
     */
    data class NoPathFound(
        val origin: GeoCoordinate,
        val destination: GeoCoordinate,
        val reason: String = "No valid route found between coordinates"
    ) : RoutingError("No path found from $origin to $destination: $reason")

    /**
     * No path exists between the specified graph node IDs or coordinate labels.
     */
    data class NoRouteFound(
        val origin: String,
        val destination: String,
        val reason: String = "No valid route found between $origin and $destination"
    ) : RoutingError("No route found from $origin to $destination: $reason")

    /**
     * One or more coordinates are invalid or cannot be snapped to the road network.
     */
    data class InvalidCoordinate(
        val coordinate: GeoCoordinate,
        val reason: String = "Coordinate is outside supported bounds or far from roads"
    ) : RoutingError("Invalid coordinate $coordinate: $reason")

    /**
     * Input parameters (such as identical origin/destination or missing node IDs) are invalid.
     */
    data class InvalidInput(
        override val message: String
    ) : RoutingError(message)

    /**
     * The road network graph is empty, corrupted, or not loaded.
     */
    data class GraphNotAvailable(
        val details: String = "Road graph is not loaded or empty"
    ) : RoutingError("Graph not available: $details")

    /**
     * Route calculation exceeded the allowed time or iteration budget.
     */
    data class RoutingTimeout(
        val timeoutMs: Long
    ) : RoutingError("Routing timed out after ${timeoutMs}ms")

    /**
     * A network error occurred while fetching routing data.
     */
    data class NetworkFailure(
        override val message: String,
        override val cause: Throwable? = null
    ) : RoutingError(message, cause)

    /**
     * The routing provider returned a response without valid road geometry (`shape`/`points` missing or < 2 vertices).
     */
    data class RouteGeometryMissing(
        val providerName: String,
        val reason: String
    ) : RoutingError("$providerName returned missing or degenerate route geometry: $reason")

    /**
     * The routing provider is temporarily unavailable (e.g., circuit breaker OPEN, HTTP 429/502/503/504, or blank endpoint).
     */
    data class ProviderUnavailable(
        val providerName: String,
        val reason: String
    ) : RoutingError("$providerName is unavailable: $reason")

    /**
     * The routing provider returned a malformed, truncated, or schema-incompatible payload.
     */
    data class MalformedResponse(
        val providerName: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : RoutingError("$providerName returned malformed response: $reason", cause)

    /**
     * A requested preference cannot be honored in strict mode by the provider.
     */
    data class UnsupportedPreference(
        val providerName: String,
        val preferenceName: String
    ) : RoutingError("$providerName does not support requested preference: $preferenceName")

    /**
     * An internal routing solver or reconstruction failure occurred.
     */
    data class InternalError(
        override val message: String,
        override val cause: Throwable? = null
    ) : RoutingError(message, cause)

    /**
     * An unexpected error occurred during routing.
     */
    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : RoutingError(message, cause)
}