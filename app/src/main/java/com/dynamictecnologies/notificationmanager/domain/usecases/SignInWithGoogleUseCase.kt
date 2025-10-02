package com.dynamictecnologies.notificationmanager.domain.usecases

import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository

/**
 * Caso de uso para iniciar sesión con Google.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja la lógica de negocio de Google Sign-In
 * - DIP: Depende de la abstracción AuthRepository
 * - Clean Architecture: Caso de uso en la capa de dominio
 */
class SignInWithGoogleUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        return authRepository.signInWithGoogle(idToken)
    }
}
