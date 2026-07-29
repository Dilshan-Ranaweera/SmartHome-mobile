package com.example.smarthome.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

import com.example.smarthome.ui.DashboardScreen
import com.example.smarthome.ui.DeviceDetailScreen
import com.example.smarthome.ui.FloorDetailScreen
import com.example.smarthome.ui.ReportsScreen

@Serializable
object DashboardRoute

@Serializable
data class FloorDetailRoute(val floorId: String)

@Serializable
data class DeviceDetailRoute(val deviceId: String)

@Serializable
object ReportsRoute

@Composable
fun SmartHomeNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute
    ) {
        composable<DashboardRoute> {
            DashboardScreen()
        }
        composable<FloorDetailRoute> { backStackEntry ->
            val route: FloorDetailRoute = backStackEntry.toRoute()
            FloorDetailScreen(route.floorId)
        }
        composable<DeviceDetailRoute> { backStackEntry ->
            val route: DeviceDetailRoute = backStackEntry.toRoute()
            DeviceDetailScreen(route.deviceId)
        }
        composable<ReportsRoute> {
            ReportsScreen()
        }
    }
}
