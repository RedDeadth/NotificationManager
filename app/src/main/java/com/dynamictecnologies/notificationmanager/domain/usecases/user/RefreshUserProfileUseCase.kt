package com.dynamictecnologies.notificationmanager.domain.usecases.user

import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository

/**
 * Caso de uso para refrescar el perfil del usuario desde el servidor.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja el refresco del perfil
 * - DIP: Depende de la abstracción UserProfileRepository
 * - Clean Architecture: Caso de uso en la capa de dominio
 */
class RefreshUserProfileUseCase(
    private val userProfileRepository: UserProfileRepository
) {
    /**
     * Refresca el perfil del usuario desde el servidor
     */
    suspend operator fun invoke(): Result<UserProfile> {
        return userProfileRepository.refreshProfile()
    }
}
