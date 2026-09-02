package com.sandolpin.weatherquake.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sandolpin.weatherquake.ui.eew.EewFullHistoryScreen
import com.sandolpin.weatherquake.ui.quake.QuakeDetailScreen
import com.sandolpin.weatherquake.ui.quake.QuakeFullHistoryScreen
import com.sandolpin.weatherquake.ui.quake.QuakeListScreen
import com.sandolpin.weatherquake.ui.settings.SettingsScreen
import com.sandolpin.weatherquake.ui.weather.WeatherScreen

private object Routes {
    const val WEATHER = "weather"
    const val QUAKE_LIST = "quake_list"
    const val QUAKE_DETAIL = "quake_detail/{id}"
    const val QUAKE_HISTORY = "quake_history"
    const val SETTINGS = "settings"
    const val EEW_HISTORY = "eew_history"

    fun quakeDetail(id: String) = "quake_detail/$id"
}

private val TAB_ORDER = listOf(Routes.WEATHER, Routes.QUAKE_LIST, Routes.SETTINGS)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabAwareEnterTransition(): EnterTransition {
    val initialIndex = TAB_ORDER.indexOf(initialState.destination.route)
    val targetIndex = TAB_ORDER.indexOf(targetState.destination.route)
    if (initialIndex < 0 || targetIndex < 0 || initialIndex == targetIndex) {
        return fadeIn(animationSpec = tween(350))
    }
    val direction = if (targetIndex > initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
    return slideIntoContainer(direction, animationSpec = tween(350)) + fadeIn(animationSpec = tween(350))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabAwareExitTransition(): ExitTransition {
    val initialIndex = TAB_ORDER.indexOf(initialState.destination.route)
    val targetIndex = TAB_ORDER.indexOf(targetState.destination.route)
    if (initialIndex < 0 || targetIndex < 0 || initialIndex == targetIndex) {
        return fadeOut(animationSpec = tween(350))
    }
    val direction = if (targetIndex > initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
    return slideOutOfContainer(direction, animationSpec = tween(350)) + fadeOut(animationSpec = tween(350))
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val topLevelRoutes = setOf(Routes.WEATHER, Routes.QUAKE_LIST, Routes.SETTINGS)
    val selectedTab = when (currentRoute) {
        Routes.QUAKE_LIST -> AppTab.QUAKE
        Routes.SETTINGS -> AppTab.SETTINGS
        else -> AppTab.WEATHER
    }

    Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = Routes.WEATHER,
                enterTransition = { tabAwareEnterTransition() },
                exitTransition = { tabAwareExitTransition() },
                popEnterTransition = { tabAwareEnterTransition() },
                popExitTransition = { tabAwareExitTransition() }
            ) {
                composable(Routes.WEATHER) { WeatherScreen() }

                composable(Routes.QUAKE_LIST) {
                    QuakeListScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        onCardClick = { id -> navController.navigate(Routes.quakeDetail(id)) },
                        onOpenHistory = { navController.navigate(Routes.QUAKE_HISTORY) }
                    )
                }

                composable(
                    Routes.QUAKE_DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStackEntry2 ->
                    val id = backStackEntry2.arguments?.getString("id")
                    QuakeDetailScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        quakeId = id,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.QUAKE_HISTORY) {
                    QuakeFullHistoryScreen(
                        onBack = { navController.popBackStack() },
                        onQuakeClick = { id -> navController.navigate(Routes.quakeDetail(id)) }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(onOpenEewHistory = { navController.navigate(Routes.EEW_HISTORY) })
                }

                composable(Routes.EEW_HISTORY) {
                    EewFullHistoryScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        if (currentRoute in topLevelRoutes) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BottomNavBar(selectedTab = selectedTab) { tab ->
                    val route = when (tab) {
                        AppTab.WEATHER -> Routes.WEATHER
                        AppTab.QUAKE -> Routes.QUAKE_LIST
                        AppTab.SETTINGS -> Routes.SETTINGS
                    }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }
}