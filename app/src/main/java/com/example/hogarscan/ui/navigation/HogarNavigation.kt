package com.example.hogarscan.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.hogarscan.ui.components.HogarIcon
import com.example.hogarscan.ui.components.HogarIconImage

enum class RootDestination(
    val label: String,
    val icon: HogarIcon
) {
    Home("Inicio", HogarIcon.Home),
    History("Historial", HogarIcon.History),
    Statistics("Estadísticas", HogarIcon.Statistics),
    Account("Cuenta", HogarIcon.Account)
}

@Composable
fun HogarBottomNavigation(
    selected: RootDestination,
    onSelect: (RootDestination) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val activeColor = colors.primary
    val inactiveColor = colors.onSurfaceVariant

    NavigationBar(
        containerColor = colors.surface
    ) {
        RootDestination.entries.forEach { destination ->
            val isSelected = destination == selected

            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(destination) },
                icon = {
                    HogarIconImage(
                        icon = destination.icon,
                        tint = true,
                        tintColor = if (isSelected) {
                            activeColor
                        } else {
                            inactiveColor
                        }
                    )
                },
                label = {
                    Text(destination.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = activeColor,
                    unselectedIconColor = inactiveColor,
                    selectedTextColor = activeColor,
                    unselectedTextColor = inactiveColor,
                    indicatorColor = colors.surfaceVariant
                )
            )
        }
    }
}