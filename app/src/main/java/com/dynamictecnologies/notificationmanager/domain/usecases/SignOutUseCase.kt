package com.dynamictecnologies.notificationmanager.domain.usecases

import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository

/**
 * Caso de uso para cerrar sesión.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja la lógica de negocio de cierre de sesión
 * - DIP: Depende de la abstracción AuthRepository
 * - Clean Architecture: Caso de uso en la capa de dominio
 */
class SignOutUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return authRepository.signOut()
    }
}
