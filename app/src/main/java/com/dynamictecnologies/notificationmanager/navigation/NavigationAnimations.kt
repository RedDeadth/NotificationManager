package com.dynamictecnologies.notificationmanager.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*

object NavigationAnimations {
    // Duración más larga para que las transiciones sean más suaves
    private const val ANIMATION_DURATION = 400

    // Usa EaseInOut para un efecto más fluido
    private val animationEasing = FastOutSlowInEasing

    fun enterTransition(): EnterTransition {
        return fadeIn(
            // Inicia con alpha 0.3 para evitar el flash de aparición brusca
            initialAlpha = 0.3f,
            animationSpec = tween(ANIMATION_DURATION, easing = animationEasing)
        ) + slideInHorizontally(
            animationSpec = tween(ANIMATION_DURATION, easing = animationEasing),
            initialOffsetX = { fullWidth -> fullWidth / 2 } // Desplazamiento menor
        )
    }

    fun exitTransition(): ExitTransition {
        return fadeOut(
            // Mantener algo de visibilidad hasta el final
            targetAlpha = 0.0f,
            animationSpec = tween(ANIMATION_DURATION, easing = animationEasing)
        ) + slideOutHorizontally(
            animationSpec = tween(ANIMATION_DURATION, easing = animationEasing),
            targetOffsetX = { fullWidth -> -fullWidth / 4 } // Desplazamiento menor para salir
        )
    }

    fun popEnterTransition(): EnterTransition {
        return fadeIn(
            initialAlpha = 0.3f,
            animationSpec = tween(ANIMATION_DURATION, easing = animationEasing)
        ) + slideInHorizontally(
            animationSpec = tween(ANIMATION_DURATION, easing = animationEasing),
            initialOffsetX = { fullWidth -> -fullWidth / 4 }
        )
    }

    fun popExitTransition(): ExitTransition {
        return fadeOut(
            targetAlpha = 0.0f,
            animationSpec = tween(ANIMATION_DURATION, easing = animationEasing)
        ) + slideOutHorizontally(
            animationSpec = tween(ANIMATION_DURATION, easing = animationEasing),
            targetOffsetX = { fullWidth -> fullWidth / 2 }
        )
    }
}