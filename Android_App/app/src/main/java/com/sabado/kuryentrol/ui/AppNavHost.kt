package com.sabado.kuryentrol.ui

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sabado.kuryentrol.ui.dashboard.DashboardScreen
import com.sabado.kuryentrol.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object Settings : Screen("settings", "Settings")
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Screen.Dashboard.route,
                    onClick = { navController.navigate(Screen.Dashboard.route) {
                        popUpTo(0)
                        launchSingleTop = true
                    } },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                    label = { Text(Screen.Dashboard.label) }
                )
                NavigationBarItem(
                    selected = currentRoute == Screen.Settings.route,
                    onClick = { navController.navigate(Screen.Settings.route) {
                        popUpTo(0)
                        launchSingleTop = true
                    } },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text(Screen.Settings.label) }
                )
            }
        }
    ) { paddingValues ->
        NavHost(navController = navController, startDestination = Screen.Dashboard.route, modifier = Modifier.padding(paddingValues)) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
