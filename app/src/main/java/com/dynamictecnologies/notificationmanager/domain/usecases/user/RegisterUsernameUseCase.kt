package com.dynamictecnologies.notificationmanager.domain.usecases.user

import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository

/**
 * Caso de uso para registrar un username para el usuario actual.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja la lógica de negocio de registrar username
 * - DIP: Depende de la abstracción UserProfileRepository
 * - Clean Architecture: Caso de uso en la capa de dominio
 */
class RegisterUsernameUseCase(
    private val userProfileRepository: UserProfileRepository
) {
    /**
     * Registra un username para el usuario autenticado actual
     * 
     * @param username El nombre de usuario a registrar
     * @return Result con el perfil creado o error
     */
    suspend operator fun invoke(username: String): Result<UserProfile> {
        // El repositorio ya maneja validación, verficación de disponibilidad, etc.
        return userProfileRepository.registerUsername(username)
    }
}
