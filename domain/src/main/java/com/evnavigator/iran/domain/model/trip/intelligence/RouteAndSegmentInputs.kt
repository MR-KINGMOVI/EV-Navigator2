package com.evnavigator.iran.domain.model.trip.intelligence

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Elevation
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.Soc

/**
 * Encapsulates route and segment related inputs for trip intelligence calculations.
 */
data class RouteAndSegmentInputs(
    val route: Route? = null,
    val routeDistanceMeter: Double? = null,
    val elevationGainMeters: Double? = null,
    val elevationDescentMeters: Double? = null
)