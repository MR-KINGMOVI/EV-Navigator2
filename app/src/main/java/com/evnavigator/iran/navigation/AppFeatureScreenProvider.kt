package com.evnavigator.iran.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.evnavigator.iran.ui.screen.ChargingScreen
import com.evnavigator.iran.ui.screen.HomeScreen
import com.evnavigator.iran.ui.screen.MapScreen
import com.evnavigator.iran.ui.screen.SettingsScreen
import com.evnavigator.iran.ui.screen.TripPlannerScreen
import com.evnavigator.iran.ui.screen.VehicleScreen
import com.evnavigator.iran.ui.screen.analytics.EnergyAnalyticsScreen
import com.evnavigator.iran.ui.screen.charging.ChargerDetailScreen
import com.evnavigator.iran.ui.screen.favorites.FavoritesScreen
import com.evnavigator.iran.ui.screen.history.TripHistoryScreen
import com.evnavigator.iran.ui.screen.intelligence.TripIntelligenceViewModel
import com.evnavigator.iran.ui.screen.journey.ActiveJourneyScreen
import com.evnavigator.iran.ui.screen.journey.ActiveJourneyViewModel
import com.evnavigator.iran.ui.screen.offline.OfflineMapManagerScreen
import com.evnavigator.iran.ui.screen.vehicle.VehicleDetailScreen

/**
 * V11 Composition Root implementation of [NavigationFeatureScreenProvider] in `:app`.
 * Bridges `:navigation` routes with `:feature` Composable screens without `:navigation`
 * depending on `:feature`.
 */
object AppFeatureScreenProvider : NavigationFeatureScreenProvider {

    @Composable
    override fun RenderMapScreen(
        onStartNavigation: () -> Unit,
        onNavigateToChargerDetail: (String) -> Unit
    ) {
        MapScreen(
            onStartNavigation = onStartNavigation,
            onNavigateToChargerDetail = onNavigateToChargerDetail
        )
    }

    @Composable
    override fun RenderTripPlannerScreen(
        initialOrigin: String,
        initialDestination: String,
        onStartNavigation: () -> Unit
    ) {
        val tripViewModel: TripIntelligenceViewModel = hiltViewModel()
        TripPlannerScreen(
            viewModel = tripViewModel,
            initialOrigin = initialOrigin,
            initialDestination = initialDestination,
            onStartNavigation = {
                tripViewModel.prepareNavigationHandoff()
                onStartNavigation()
            }
        )
    }

    @Composable
    override fun RenderChargingScreen(
        onNavigateToRoute: (Double, Double) -> Unit,
        onNavigateToDetail: (String) -> Unit
    ) {
        ChargingScreen(
            onNavigateToRoute = onNavigateToRoute,
            onNavigateToDetail = onNavigateToDetail
        )
    }

    @Composable
    override fun RenderVehicleScreen(
        onNavigateToDetail: (String) -> Unit
    ) {
        VehicleScreen(
            onNavigateToDetail = onNavigateToDetail
        )
    }

    @Composable
    override fun RenderHomeScreen(
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
    ) {
        HomeScreen(
            onNavigateToMap = onNavigateToMap,
            onNavigateToTripPlanner = onNavigateToTripPlanner,
            onNavigateToNavigation = onNavigateToNavigation,
            onNavigateToCharging = onNavigateToCharging,
            onNavigateToVehicles = onNavigateToVehicles,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAnalytics = onNavigateToAnalytics,
            onNavigateToHistory = onNavigateToHistory,
            onNavigateToFavorites = onNavigateToFavorites,
            onLaunchCorridorTrip = onLaunchCorridorTrip
        )
    }

    @Composable
    override fun RenderActiveJourneyScreen(
        destLat: Double?,
        destLon: Double?,
        onNavigateBack: () -> Unit
    ) {
        val journeyViewModel: ActiveJourneyViewModel = hiltViewModel()
        LaunchedEffect(destLat, destLon) {
            if (destLat != null && destLon != null) {
                journeyViewModel.navigateToCoordinate(destLat, destLon)
            }
        }
        ActiveJourneyScreen(
            viewModel = journeyViewModel,
            onNavigateBack = onNavigateBack
        )
    }

    @Composable
    override fun RenderSettingsScreen(
        onNavigateToOfflineMaps: () -> Unit
    ) {
        SettingsScreen(
            onNavigateToOfflineMaps = onNavigateToOfflineMaps
        )
    }

    @Composable
    override fun RenderOfflineMapManagerScreen(
        onNavigateBack: () -> Unit
    ) {
        OfflineMapManagerScreen(
            onNavigateBack = onNavigateBack
        )
    }

    @Composable
    override fun RenderTripHistoryScreen(
        onNavigateBack: () -> Unit
    ) {
        TripHistoryScreen(
            onNavigateBack = onNavigateBack
        )
    }

    @Composable
    override fun RenderEnergyAnalyticsScreen(
        onNavigateBack: () -> Unit
    ) {
        EnergyAnalyticsScreen(
            onNavigateBack = onNavigateBack
        )
    }

    @Composable
    override fun RenderFavoritesScreen(
        onNavigateBack: () -> Unit,
        onNavigateToRoute: (String, String) -> Unit,
        onNavigateToStation: () -> Unit
    ) {
        FavoritesScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToRoute = onNavigateToRoute,
            onNavigateToStation = onNavigateToStation
        )
    }

    @Composable
    override fun RenderChargerDetailScreen(
        chargerId: String,
        onNavigateBack: () -> Unit,
        onStartNavigation: (Double, Double) -> Unit
    ) {
        ChargerDetailScreen(
            chargerId = chargerId,
            onNavigateBack = onNavigateBack,
            onStartNavigation = onStartNavigation
        )
    }

    @Composable
    override fun RenderVehicleDetailScreen(
        vehicleId: String,
        onNavigateBack: () -> Unit
    ) {
        VehicleDetailScreen(
            vehicleId = vehicleId,
            onNavigateBack = onNavigateBack
        )
    }
}
