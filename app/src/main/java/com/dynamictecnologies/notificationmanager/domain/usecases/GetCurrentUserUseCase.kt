package com.dynamictecnologies.notificationmanager.domain.usecases

import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para obtener el usuario actual.
 * 
 * - Clean Architecture: Caso de uso en la capa de dominio
 */
class GetCurrentUserUseCase(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<User?> {
        return authRepository.getCurrentUser()
    }
}
