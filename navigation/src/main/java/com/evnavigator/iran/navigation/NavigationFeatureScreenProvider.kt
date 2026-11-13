package com.evnavigator.iran.navigation

import androidx.compose.runtime.Composable

/**
 * V11 Composition Root Contract for Feature Screens.
 *
 * Allows `:navigation` to remain a pure route & dock orchestration shell without
 * importing concrete `:feature` screen implementations. Concrete bindings are
 * supplied by the `:app` composition root (`AppFeatureScreenProvider`).
 */
interface NavigationFeatureScreenProvider {

    @Composable
    fun RenderMapScreen(
        onStartNavigation: () -> Unit,
        onNavigateToChargerDetail: (String) -> Unit
    )

    @Composable
    fun RenderTripPlannerScreen(
        initialOrigin: String = "",
        initialDestination: String = "",
        onStartNavigation: () -> Unit
    )

    @Composable
    fun RenderChargingScreen(
        onNavigateToRoute: (Double, Double) -> Unit,
        onNavigateToDetail: (String) -> Unit
    )

    @Composable
    fun RenderVehicleScreen(
        onNavigateToDetail: (String) -> Unit
    )

    @Composable
    fun RenderHomeScreen(
        onNavigateToMap: () -> Unit,
        onNavigateToTripPlanner: () -> Unit,
        onNavigateToNavigation: () -> Unit,
        onNavigateToCharging: () -> Unit,
        onNavigateToVehicles: () -> Unit,
        onNavigateToSettings: () -> Unit,
        onNavigateToAnalytics: () -> Unit,
        onNavigateToHistory: () -> Unit,
        onNavigateToFavorites: () -> Unit,
        onLaunchCorridorTrip: (String, String) -> Unit
    )

    @Composable
    fun RenderActiveJourneyScreen(
        destLat: Double?,
        destLon: Double?,
        onNavigateBack: () -> Unit
    )

    @Composable
    fun RenderSettingsScreen(
        onNavigateToOfflineMaps: () -> Unit
    )

    @Composable
    fun RenderOfflineMapManagerScreen(
        onNavigateBack: () -> Unit
    )

    @Composable
    fun RenderTripHistoryScreen(
        onNavigateBack: () -> Unit
    )

    @Composable
    fun RenderEnergyAnalyticsScreen(
        onNavigateBack: () -> Unit
    )

    @Composable
    fun RenderFavoritesScreen(
        onNavigateBack: () -> Unit,
        onNavigateToRoute: (String, String) -> Unit,
        onNavigateToStation: () -> Unit = {}
    )

    @Composable
    fun RenderChargerDetailScreen(
        chargerId: String,
        onNavigateBack: () -> Unit,
        onStartNavigation: (Double, Double) -> Unit
    )

    @Composable
    fun RenderVehicleDetailScreen(
        vehicleId: String,
        onNavigateBack: () -> Unit
    )
}
