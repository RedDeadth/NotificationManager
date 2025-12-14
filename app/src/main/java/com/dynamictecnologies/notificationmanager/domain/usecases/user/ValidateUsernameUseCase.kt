package com.dynamictecnologies.notificationmanager.domain.usecases.user

import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository

/**
 * Caso de uso para validar disponibilidad de un username.
 * 
 * - Clean Architecture: Caso de uso en la capa de dominio
 */
class ValidateUsernameUseCase(
    private val userProfileRepository: UserProfileRepository
) {
    /**
     * Verifica si un username está disponible
     * 
     * @param username El nombre de usuario a verificar
     * @return true si está disponible, false si ya está en uso
     */
    suspend operator fun invoke(username: String): Boolean {
        return userProfileRepository.isUsernameAvailable(username)
    }
}
