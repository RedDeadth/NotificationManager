package com.dynamictecnologies.notificationmanager.presentation.core.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.dynamictecnologies.notificationmanager.presentation.core.navigation.AppScreen

/**
 * Componente unificado de BottomBar.
 * Centraliza la navegación principal de la aplicación.
 */
@Composable
fun AppBottomBar(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit
) {
    NavigationBar {
        AppScreen.values().forEach { screen ->
            NavigationBarItem(
                selected = currentScreen == screen,
                onClick = { onScreenSelected(screen) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = if (currentScreen == screen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                label = { 
                    Text(
                        text = screen.title,
                        color = if (currentScreen == screen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            )
        }
    }
}
