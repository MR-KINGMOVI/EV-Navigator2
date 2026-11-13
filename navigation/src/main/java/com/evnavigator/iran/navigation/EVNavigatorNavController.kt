package com.evnavigator.iran.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

/**
 * Type-safe navigation controller for EV Navigator Iran
 */
class EVNavigatorNavController(
    val navController: NavHostController
) {
    // 5 Primary Hubs
    fun navigateToMap() {
        navController.navigate(EVNavigatorDestinations.Map.route) {
            launchSingleTop = true
        }
    }
    
    fun navigateToPlan() {
        navController.navigate(EVNavigatorDestinations.Plan.route) {
            launchSingleTop = true
        }
    }

    fun navigateToChargers() {
        navController.navigate(EVNavigatorDestinations.Chargers.route) {
            launchSingleTop = true
        }
    }

    fun navigateToGarage() {
        navController.navigate(EVNavigatorDestinations.Garage.route) {
            launchSingleTop = true
        }
    }

    fun navigateToActivity() {
        navController.navigate(EVNavigatorDestinations.Activity.route) {
            launchSingleTop = true
        }
    }

    // Secondary & Active Navigation
    fun navigateToNavigation() {
        navController.navigate(EVNavigatorDestinations.Navigation.route)
    }

    fun navigateToSettings() {
        navController.navigate(EVNavigatorDestinations.Settings.route)
    }
    
    fun navigateToFavorites() {
        navController.navigate(EVNavigatorDestinations.Favorites.route)
    }
    
    fun navigateToDiagnostics() {
        navController.navigate(EVNavigatorDestinations.Diagnostics.route)
    }
    
    fun navigateToTripPreview(origin: String, destination: String) {
        navController.navigate("trip_preview/$origin/$destination")
    }
    
    fun navigateToChargerDetail(chargerId: String) {
        navController.navigate("charger_detail/$chargerId")
    }
    
    fun navigateToVehicleDetail(vehicleId: String) {
        navController.navigate("vehicle_detail/$vehicleId")
    }

    // Legacy Aliases
    fun navigateToHome() = navigateToMap()
    fun navigateToTripPlanner() = navigateToPlan()
    fun navigateToCharging() = navigateToChargers()
    fun navigateToVehicles() = navigateToGarage()
    fun navigateToHistory() = navigateToActivity()
    
    fun goBack() {
        navController.popBackStack()
    }
    
    fun canGoBack(): Boolean = navController.previousBackStackEntry != null
}

/**
 * Creates a typed navigation controller for the application
 */
@Composable
fun rememberEVNavigatorNavController(): EVNavigatorNavController {
    val navController = rememberNavController()
    return remember(navController) {
        EVNavigatorNavController(navController)
    }
}
