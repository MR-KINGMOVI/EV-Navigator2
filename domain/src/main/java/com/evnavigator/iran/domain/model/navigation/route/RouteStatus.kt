package com.evnavigator.iran.domain.model.navigation.route

/**
 * Status of the route during navigation.
 */
enum class RouteStatus {
    ACTIVE,
    REROUTING,
    RECALCULATING,
    OFF_ROUTE,
    COMPLETED
}
