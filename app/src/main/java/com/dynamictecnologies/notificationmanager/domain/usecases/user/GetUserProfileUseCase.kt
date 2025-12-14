package com.dynamictecnologies.notificationmanager.domain.usecases.user

import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para obtener el perfil del usuario actual.
 * 
 * - Clean Architecture: Caso de uso en la capa de dominio
 */
class GetUserProfileUseCase(
    private val userProfileRepository: UserProfileRepository
) {
    /**
     * Obtiene el perfil del usuario como Flow reactivo
     */
    operator fun invoke(): Flow<UserProfile?> {
        return userProfileRepository.getUserProfile()
    }
}
