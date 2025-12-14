package com.dynamictecnologies.notificationmanager.domain.usecases

import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository

/**
 * Caso de uso para validar si la sesión es válida.
 * 
 * - Clean Architecture: Caso de uso en la capa de dominio
 */
class ValidateSessionUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Boolean {
        return authRepository.isSessionValid()
    }
}
