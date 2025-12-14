package com.dynamictecnologies.notificationmanager.domain.usecases

import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository

/**
 * Caso de uso para cerrar sesión del usuario actual.
 * 
 * Mejoras aplicadas:
 * - Movido UserService del Repository al Use Case (arquitectura correcta)
 * - Lógica de negocio de cleanup en el caso de uso
 * 
 * - Clean Architecture: Lógica de negocio en capa de dominio
 */
class SignOutUseCase(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) {
    /**
     * Cierra la sesión del usuario y limpia todos los servicios
     */
    suspend operator fun invoke(): Result<Unit> {
        return try {
            // 1. Cerrar sesión en el repositorio (Firebase + local storage)
            authRepository.signOut().getOrThrow()
            
            // 2. Limpiar caché del perfil
            userProfileRepository.clearCache()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
