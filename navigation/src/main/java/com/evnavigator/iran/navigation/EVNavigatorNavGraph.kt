package com.evnavigator.iran.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.navigation.components.FloatingCockpitNavDock

data class PrimaryNavItem(
    val route: String,
    val titleExtractor: (com.evnavigator.iran.designsystem.locale.AppStrings) -> String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val primaryNavItems = listOf(
    PrimaryNavItem(EVNavigatorDestinations.Map.route, { it.tabMap }, EVIcons.Map),
    PrimaryNavItem(EVNavigatorDestinations.Plan.route, { it.tabPlan }, EVIcons.Route),
    PrimaryNavItem(EVNavigatorDestinations.Chargers.route, { it.tabChargers }, EVIcons.Charging),
    PrimaryNavItem(EVNavigatorDestinations.Garage.route, { it.tabGarage }, EVIcons.Vehicle),
    PrimaryNavItem(EVNavigatorDestinations.Activity.route, { it.tabActivity }, EVIcons.Home)
)

/**
 * V11 Re-engineered root navigation shell for EV Navigator Iran.
 * Decoupled from `:feature` via [NavigationFeatureScreenProvider] injected at the `:app` composition root.
 */
@Composable
fun EVNavigatorNavGraph(
    navController: NavHostController,
    screenProvider: NavigationFeatureScreenProvider,
    startDestination: String = EVNavigatorDestinations.Map.route
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute?.startsWith(EVNavigatorDestinations.Navigation.route) != true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                FloatingCockpitNavDock(
                    items = primaryNavItems,
                    currentRoute = currentRoute,
                    onItemSelected = { item ->
                        if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    chargerBadgeCount = "110+"
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. Primary Hub: Map (Default)
            composable(EVNavigatorDestinations.Map.route) {
                screenProvider.RenderMapScreen(
                    onStartNavigation = { navController.navigate(EVNavigatorDestinations.Navigation.route) },
                    onNavigateToChargerDetail = { chargerId ->
                        navController.navigate("charger_detail/$chargerId")
                    }
                )
            }

            // 2. Primary Hub: Plan (Trip Planner)
            composable(EVNavigatorDestinations.Plan.route) {
                screenProvider.RenderTripPlannerScreen(
                    onStartNavigation = {
                        navController.navigate(EVNavigatorDestinations.Navigation.route)
                    }
                )
            }
            composable(EVNavigatorDestinations.TripPlanner.route) {
                screenProvider.RenderTripPlannerScreen(
                    onStartNavigation = {
                        navController.navigate(EVNavigatorDestinations.Navigation.route)
                    }
                )
            }

            // 3. Primary Hub: Chargers (Charging Station Radar)
            composable(EVNavigatorDestinations.Chargers.route) {
                screenProvider.RenderChargingScreen(
                    onNavigateToRoute = { lat, lon ->
                        navController.navigate("navigation?destLat=$lat&destLon=$lon")
                    },
                    onNavigateToDetail = { chargerId ->
                        navController.navigate("charger_detail/$chargerId")
                    }
                )
            }
            composable(EVNavigatorDestinations.Charging.route) {
                screenProvider.RenderChargingScreen(
                    onNavigateToRoute = { lat, lon ->
                        navController.navigate("navigation?destLat=$lat&destLon=$lon")
                    },
                    onNavigateToDetail = { chargerId ->
                        navController.navigate("charger_detail/$chargerId")
                    }
                )
            }

            // 4. Primary Hub: Garage (Vehicle Management)
            composable(EVNavigatorDestinations.Garage.route) {
                screenProvider.RenderVehicleScreen(
                    onNavigateToDetail = { vehicleId ->
                        navController.navigate("vehicle_detail/$vehicleId")
                    }
                )
            }
            composable(EVNavigatorDestinations.Vehicles.route) {
                screenProvider.RenderVehicleScreen(
                    onNavigateToDetail = { vehicleId ->
                        navController.navigate("vehicle_detail/$vehicleId")
                    }
                )
            }

            // 5. Primary Hub: Activity (Telemetry, Past Journeys & Corridors)
            composable(EVNavigatorDestinations.Activity.route) {
                screenProvider.RenderHomeScreen(
                    onNavigateToMap = { navController.navigate(EVNavigatorDestinations.Map.route) },
                    onNavigateToTripPlanner = { navController.navigate(EVNavigatorDestinations.Plan.route) },
                    onNavigateToNavigation = { navController.navigate(EVNavigatorDestinations.Navigation.route) },
                    onNavigateToCharging = { navController.navigate(EVNavigatorDestinations.Chargers.route) },
                    onNavigateToVehicles = { navController.navigate(EVNavigatorDestinations.Garage.route) },
                    onNavigateToSettings = { navController.navigate(EVNavigatorDestinations.Settings.route) },
                    onNavigateToAnalytics = { navController.navigate(EVNavigatorDestinations.Analytics.route) },
                    onNavigateToHistory = { navController.navigate(EVNavigatorDestinations.History.route) },
                    onNavigateToFavorites = { navController.navigate(EVNavigatorDestinations.Favorites.route) },
                    onLaunchCorridorTrip = { origin, destination ->
                        navController.navigate("trip_preview/$origin/$destination")
                    }
                )
            }
            composable(EVNavigatorDestinations.Home.route) {
                screenProvider.RenderHomeScreen(
                    onNavigateToMap = { navController.navigate(EVNavigatorDestinations.Map.route) },
                    onNavigateToTripPlanner = { navController.navigate(EVNavigatorDestinations.Plan.route) },
                    onNavigateToNavigation = { navController.navigate(EVNavigatorDestinations.Navigation.route) },
                    onNavigateToCharging = { navController.navigate(EVNavigatorDestinations.Chargers.route) },
                    onNavigateToVehicles = { navController.navigate(EVNavigatorDestinations.Garage.route) },
                    onNavigateToSettings = { navController.navigate(EVNavigatorDestinations.Settings.route) },
                    onNavigateToAnalytics = { navController.navigate(EVNavigatorDestinations.Analytics.route) },
                    onNavigateToHistory = { navController.navigate(EVNavigatorDestinations.History.route) },
                    onNavigateToFavorites = { navController.navigate(EVNavigatorDestinations.Favorites.route) },
                    onLaunchCorridorTrip = { origin, destination ->
                        navController.navigate("trip_preview/$origin/$destination")
                    }
                )
            }

            // Active Turn-by-Turn Navigation Screen (Full Screen HUD)
            composable(
                route = "navigation?destLat={destLat}&destLon={destLon}",
                arguments = listOf(
                    navArgument("destLat") { defaultValue = "" },
                    navArgument("destLon") { defaultValue = "" }
                )
            ) { backStackEntry ->
                val destLat = backStackEntry.arguments?.getString("destLat")?.toDoubleOrNull()
                val destLon = backStackEntry.arguments?.getString("destLon")?.toDoubleOrNull()
                screenProvider.RenderActiveJourneyScreen(
                    destLat = destLat,
                    destLon = destLon,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Secondary Screens
            composable(EVNavigatorDestinations.Settings.route) {
                screenProvider.RenderSettingsScreen(
                    onNavigateToOfflineMaps = { navController.navigate(EVNavigatorDestinations.OfflineMaps.route) }
                )
            }
            composable(EVNavigatorDestinations.Diagnostics.route) {
                screenProvider.RenderSettingsScreen(
                    onNavigateToOfflineMaps = { navController.navigate(EVNavigatorDestinations.OfflineMaps.route) }
                )
            }
            composable(EVNavigatorDestinations.OfflineMaps.route) {
                screenProvider.RenderOfflineMapManagerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(EVNavigatorDestinations.History.route) {
                screenProvider.RenderTripHistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(EVNavigatorDestinations.Analytics.route) {
                screenProvider.RenderEnergyAnalyticsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(EVNavigatorDestinations.Favorites.route) {
                screenProvider.RenderFavoritesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRoute = { origin, destination ->
                        navController.navigate("trip_preview/$origin/$destination")
                    },
                    onNavigateToStation = {
                        navController.navigate(EVNavigatorDestinations.Chargers.route)
                    }
                )
            }
            composable(EVNavigatorDestinations.SavedTrips.route) {
                screenProvider.RenderFavoritesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRoute = { origin, destination ->
                        navController.navigate("trip_preview/$origin/$destination")
                    }
                )
            }

            // Parameterized Routes
            composable(
                route = EVNavigatorDestinations.TripPreview().route,
                arguments = listOf(
                    navArgument("origin") { defaultValue = "" },
                    navArgument("destination") { defaultValue = "" }
                )
            ) { backStackEntry ->
                val origin = backStackEntry.arguments?.getString("origin").orEmpty()
                val destination = backStackEntry.arguments?.getString("destination").orEmpty()
                screenProvider.RenderTripPlannerScreen(
                    initialOrigin = origin,
                    initialDestination = destination,
                    onStartNavigation = {
                        navController.navigate(EVNavigatorDestinations.Navigation.route)
                    }
                )
            }

            composable(
                route = EVNavigatorDestinations.ChargerDetail().route,
                arguments = listOf(
                    navArgument("chargerId") { defaultValue = "" }
                )
            ) { backStackEntry ->
                val chargerId = backStackEntry.arguments?.getString("chargerId").orEmpty()
                screenProvider.RenderChargerDetailScreen(
                    chargerId = chargerId,
                    onNavigateBack = { navController.popBackStack() },
                    onStartNavigation = { lat, lon ->
                        navController.navigate("navigation?destLat=$lat&destLon=$lon")
                    }
                )
            }

            composable(
                route = EVNavigatorDestinations.VehicleDetail().route,
                arguments = listOf(
                    navArgument("vehicleId") { defaultValue = "" }
                )
            ) { backStackEntry ->
                val vehicleId = backStackEntry.arguments?.getString("vehicleId").orEmpty()
                screenProvider.RenderVehicleDetailScreen(
                    vehicleId = vehicleId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
