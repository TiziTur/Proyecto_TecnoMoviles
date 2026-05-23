package com.undef.superahorroturina.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.navigation.Routes

// Modelo de ítem para la barra de navegación inferior.
data class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = Routes.Home.route,
        labelRes = R.string.nav_home,
        icon = Icons.Default.Home,
        selectedIcon = Icons.Filled.Home
    ),
    BottomNavItem(
        route = Routes.History.route,
        labelRes = R.string.nav_history,
        icon = Icons.Default.History,
        selectedIcon = Icons.Filled.History
    ),
    BottomNavItem(
        route = Routes.Stats.route,
        labelRes = R.string.nav_stats,
        icon = Icons.Default.BarChart,
        selectedIcon = Icons.Filled.BarChart
    ),
    BottomNavItem(
        route = Routes.Profile.route,
        labelRes = R.string.nav_profile,
        icon = Icons.Default.Person,
        selectedIcon = Icons.Filled.Person
    )
)

@Composable
fun AppBottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
