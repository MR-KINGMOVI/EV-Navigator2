package com.evnavigator.iran.navigation

/**
 * Application navigation destinations with typed parameters and backward compatibility.
 */
sealed class EVNavigatorDestinations(
    val route: String,
    val arguments: List<androidx.navigation.NavArgument> = emptyList()
) {
    // The 5 Primary Product Destinations
    data object Map : EVNavigatorDestinations("map")
    data object Plan : EVNavigatorDestinations("plan")
    data object Chargers : EVNavigatorDestinations("chargers")
    data object Garage : EVNavigatorDestinations("garage")
    data object Activity : EVNavigatorDestinations("activity")

    // Active Navigation & Dedicated Secondary Flows
    data object Navigation : EVNavigatorDestinations("navigation")
    data object Settings : EVNavigatorDestinations("settings")
    data object Favorites : EVNavigatorDestinations("favorites")
    data object Diagnostics : EVNavigatorDestinations("diagnostics")
    data object OfflineMaps : EVNavigatorDestinations("offline_maps")

    // Backward-Compatibility Aliases
    data object Home : EVNavigatorDestinations("home")
    data object TripPlanner : EVNavigatorDestinations("trip_planner")
    data object Charging : EVNavigatorDestinations("charging")
    data object Vehicles : EVNavigatorDestinations("vehicles")
    data object History : EVNavigatorDestinations("history")
    data object Analytics : EVNavigatorDestinations("analytics")
    data object SavedTrips : EVNavigatorDestinations("saved_trips")
    
    // Deep links with parameters
    data class TripPreview(
        val origin: String,
        val destination: String
    ) : EVNavigatorDestinations("trip_preview/{origin}/{destination}") {
        constructor() : this("", "")
    }
    
    data class ChargerDetail(
        val chargerId: String
    ) : EVNavigatorDestinations("charger_detail/{chargerId}") {
        constructor() : this("")
    }
    
    data class VehicleDetail(
        val vehicleId: String
    ) : EVNavigatorDestinations("vehicle_detail/{vehicleId}") {
        constructor() : this("")
    }
}
