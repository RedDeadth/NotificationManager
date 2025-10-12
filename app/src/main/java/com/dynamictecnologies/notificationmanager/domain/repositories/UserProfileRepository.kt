package com.dynamictecnologies.notificationmanager.domain.repositories

import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del repositorio de perfiles de usuario en la capa de dominio.
 * No tiene dependencias de Android ni de Firebase.
 * 
 * Principios aplicados:
 * - DIP: Abstracción que no depende de detalles de implementación
 * - ISP: Interfaz segregada específica para perfiles (separada de AuthRepository)
 * - SRP: Solo operaciones relacionadas con perfiles de usuario
 * - Clean Architecture: Pertenece a la capa de dominio
 */
interface UserProfileRepository {
    
    /**
     * Obtiene el perfil del usuario actual como Flow reactivo
     */
    fun getUserProfile(): Flow<UserProfile?>
    
    /**
     * Registra un nuevo username para el usuario actual
     */
    suspend fun registerUsername(username: String): Result<UserProfile>
    
    /**
     * Verifica si un username está disponible
     */
    suspend fun isUsernameAvailable(username: String): Boolean
    
    /**
     * Verifica si el usuario actual tiene un perfil registrado
     */
    suspend fun hasUserProfile(): Boolean
    
    /**
     * Refresca el perfil desde el servidor
     */
    suspend fun refreshProfile(): Result<UserProfile>
    
    /**
     * Limpia el caché del perfil
     */
    fun clearCache()
}
