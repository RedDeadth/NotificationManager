package com.dynamictecnologies.notificationmanager.presentation.core.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.dynamictecnologies.notificationmanager.presentation.core.navigation.AppScreen

/**
 * Componente unificado de TopBar.
 * Elimina la duplicación entre AppTopBar.kt y ScaffoldDynamic.kt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String = "Notification Manager",
    currentScreen: AppScreen? = null,
    onSettingsClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    navigationIcon: @Composable (() -> Unit)? = null,
    additionalActions: @Composable (RowScope.() -> Unit) = {}
) {
    TopAppBar(
        title = {
            Text(title)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        navigationIcon = navigationIcon ?: {},
        actions = {
            // Botón de compartir (solo si la pantalla lo permite)
            if (currentScreen?.canShare == true) {
                IconButton(onClick = onShareClick) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartir",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            // Botón de ajustes
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ajustes",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            // Acciones adicionales
            additionalActions()
        }
    )
}
