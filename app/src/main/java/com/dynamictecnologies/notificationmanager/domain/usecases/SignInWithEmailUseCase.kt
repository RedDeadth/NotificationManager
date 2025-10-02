package com.dynamictecnologies.notificationmanager.domain.usecases

import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository

/**
 * Caso de uso para iniciar sesión con email y contraseña.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja la lógica de negocio de iniciar sesión
 * - DIP: Depende de la abstracción AuthRepository
 * - Clean Architecture: Caso de uso en la capa de dominio
 */
class SignInWithEmailUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        return authRepository.signInWithEmail(email, password)
    }
}
