package com.dynamictecnologies.notificationmanager.presentation.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Animaciones de navegación reutilizables.
 * Centraliza las transiciones para mantener consistencia.
 */
object NavigationAnimations {
    private const val ANIMATION_DURATION = 300

    fun enterTransition() = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(ANIMATION_DURATION)
    )

    fun exitTransition() = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(ANIMATION_DURATION)
    )

    fun popEnterTransition() = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(ANIMATION_DURATION)
    )

    fun popExitTransition() = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(ANIMATION_DURATION)
    )
}
