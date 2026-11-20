package com.example.hogarscan.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.hogarscan.ui.components.HogarIcon
import com.example.hogarscan.ui.components.HogarIconImage

enum class RootDestination(val label: String, val icon: HogarIcon) {
    Home("Inicio", HogarIcon.Home),
    History("Historial", HogarIcon.History),
    Statistics("Estadísticas", HogarIcon.Statistics),
    Account("Cuenta", HogarIcon.Account)
}

@Composable
fun HogarBottomNavigation(selected: RootDestination, onSelect: (RootDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        RootDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { HogarIconImage(destination.icon) },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
