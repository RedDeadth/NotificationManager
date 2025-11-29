package com.dynamictecnologies.notificationmanager.domain.usecases

import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository

/**
 * Caso de uso que orquesta el proceso completo de registro de usuario con username.
 * 
 * Este UseCase coordina dos operaciones:
 * 1. Registrar usuario en Firebase Auth (email + password)
 * 2. Registrar username en Firestore (perfil de usuario)
 * 
 * Principios aplicados:
 * - SRP: Encapsula toda la lógica de orquestación de registro
 * - DIP: Depende de abstracciones (repositories)
 * - Clean Architecture: Lógica de negocio en capa de dominio
 * - Separation of Concerns: ViewModel solo maneja UI state, UseCas maneja negocio
 * 
 * @param authRepository Repositorio de autenticación
 * @param userProfileRepository Repositorio de perfiles de usuario
 */
class RegisterUserWithUsernameUseCase(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) {
    /**
     * Registra un nuevo usuario con email, contraseña y username.
     * 
     * El proceso es:
     * 1. Registrar en Firebase Auth
     * 2. Si tiene éxito, registrar el username
     * 3. Si el username falla, retornamos el error (el usuario quedó registrado en Auth)
     * 
     * NOTA: No hacemos rollback del usuario de Auth si falla el username porque:
     * - El usuario puede intentar registrar otro username
     * - Firebase Auth no permite eliminar usuarios fácilmente desde el cliente
     * 
     * @param email Email del usuario
     * @param password Contraseña
     * @param username Nombre de usuario único
     * @return Result con el perfil de usuario completo o error
     */
    suspend operator fun invoke(
        email: String,
        password: String,
        username: String
    ): Result<UserProfile> {
        // Paso 1: Registrar en Firebase Auth
        val authResult = authRepository.registerWithEmail(email, password)
        
        if (authResult.isFailure) {
            return Result.failure(authResult.exceptionOrNull() ?: Exception("Error en registro"))
        }
        
        // Paso 2: Registrar username  
        val profileResult = userProfileRepository.registerUsername(username)
        
        return profileResult
    }
}
