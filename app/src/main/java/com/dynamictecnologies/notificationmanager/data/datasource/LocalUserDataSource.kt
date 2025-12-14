package com.dynamictecnologies.notificationmanager.data.datasource

import com.dynamictecnologies.notificationmanager.domain.entities.User
import java.util.concurrent.TimeUnit

/**
 * Data Source local para caché de perfiles de usuario.
 * 
 * - Clean Architecture: Pertenece a la capa de datos
 */
class LocalUserDataSource {
    
    companion object {
        private val CACHE_VALID_DURATION = TimeUnit.MINUTES.toMillis(5) // 5 minutos
    }
    
    private var cachedProfile: User? = null
    private var cachedUsername: String? = null
    private var lastFetchTime: Long = 0
    
    /**
     * Guarda el perfil en caché
     */
    fun saveProfile(profile: User) {
        cachedProfile = profile
        cachedUsername = profile.username
        lastFetchTime = System.currentTimeMillis()
    }
    
    /**
     * Obtiene el perfil del caché si es válido
     */
    fun getProfile(): User? {
        return if (isCacheValid()) cachedProfile else null
    }
    
    /**
     * Obtiene el username cacheado
     */
    fun getCachedUsername(): String? {
        return cachedUsername
    }
    
    /**
     * Verifica si el caché es válido
     */
    fun isCacheValid(): Boolean {
        val now = System.currentTimeMillis()
        return cachedProfile != null && 
               (now - lastFetchTime < CACHE_VALID_DURATION)
    }
    
    /**
     * Limpia el caché
     */
    fun clearCache() {
        cachedProfile = null
        cachedUsername = null
        lastFetchTime = 0
    }
    
    /**
     * Invalida el caché forzando un nuevo fetch
     */
    fun invalidateCache() {
        lastFetchTime = 0
    }
}
