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
import com.example.smarthome.ui.SchedulesScreen
import com.example.smarthome.ui.CamerasScreen

import com.example.smarthome.viewmodel.HomeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Serializable
object DashboardRoute

@Serializable
data class FloorDetailRoute(val floorId: String)

@Serializable
data class DeviceDetailRoute(val deviceId: String)

@Serializable
object ReportsRoute

@Serializable
object SchedulesRoute

@Serializable
object CamerasRoute

@Composable
fun SmartHomeNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute
    ) {
        composable<DashboardRoute> {
            DashboardScreen(homeViewModel)
        }
        composable<FloorDetailRoute> { backStackEntry ->
            val route: FloorDetailRoute = backStackEntry.toRoute()
            FloorDetailScreen(route.floorId, homeViewModel)
        }
        composable<DeviceDetailRoute> { backStackEntry ->
            val route: DeviceDetailRoute = backStackEntry.toRoute()
            DeviceDetailScreen(route.deviceId, homeViewModel)
        }
        composable<ReportsRoute> {
            ReportsScreen(homeViewModel)
        }
        composable<SchedulesRoute> {
            SchedulesScreen(homeViewModel)
        }
        composable<CamerasRoute> {
            CamerasScreen(homeViewModel)
        }
    }
}
