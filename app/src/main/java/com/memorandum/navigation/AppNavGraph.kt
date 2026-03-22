package com.memorandum.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memorandum.ui.entry.EntryScreen
import com.memorandum.ui.memory.MemoryScreen
import com.memorandum.ui.notifications.NotificationsScreen
import com.memorandum.ui.settings.SettingsScreen
import com.memorandum.ui.taskdetail.TaskDetailScreen
import com.memorandum.ui.tasks.TasksScreen
import com.memorandum.ui.today.TodayScreen

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomTabRoutes = listOf(
        Route.Today.route,
        Route.Tasks.route,
        Route.Memory.route,
        Route.Settings.route,
    )
    val showBottomBar = currentRoute in bottomTabRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        navController.navigate(route.route) {
                            popUpTo(Route.Today.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Today.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            // Bottom tabs
            composable(Route.Today.route) {
                TodayScreen(
                    onNavigateToEntry = { navController.navigate(Route.Entry.route) },
                    onNavigateToTask = { taskId ->
                        navController.navigate(Route.TaskDetail.create(taskId))
                    },
                )
            }
            composable(Route.Tasks.route) {
                TasksScreen(
                    onNavigateToTask = { taskId ->
                        navController.navigate(Route.TaskDetail.create(taskId))
                    },
                )
            }
            composable(Route.Memory.route) {
                MemoryScreen()
            }
            composable(Route.Settings.route) {
                SettingsScreen(
                    onNavigateToNotifications = {
                        navController.navigate(Route.Notifications.route)
                    },
                    onNavigateToModelConfig = {
                        navController.navigate(Route.ModelConfig.route)
                    },
                    onNavigateToMcpConfig = {
                        navController.navigate(Route.McpConfig.route)
                    },
                )
            }

            // Secondary pages
            composable(Route.Entry.route) {
                EntryScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Route.TaskDetail.route,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId").orEmpty()
                TaskDetailScreen(
                    taskId = taskId,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Route.Notifications.route) {
                NotificationsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Route.ModelConfig.route) {
                // ModelConfig is part of settings package
                com.memorandum.ui.settings.ModelConfigScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Route.McpConfig.route) {
                com.memorandum.ui.settings.McpConfigScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
