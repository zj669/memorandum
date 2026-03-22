package com.memorandum.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val label: String,
    val route: Route,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem("Today", Route.Today, Icons.Filled.CalendarToday, Icons.Outlined.CalendarToday),
    BottomNavItem("Tasks", Route.Tasks, Icons.Filled.Checklist, Icons.Outlined.Checklist),
    BottomNavItem("Memory", Route.Memory, Icons.Filled.Psychology, Icons.Outlined.Psychology),
    BottomNavItem("Settings", Route.Settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onTabSelected: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}
