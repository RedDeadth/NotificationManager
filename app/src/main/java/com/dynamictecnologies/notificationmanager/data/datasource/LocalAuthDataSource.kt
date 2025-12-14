package com.dynamictecnologies.notificationmanager.data.datasource

import com.dynamictecnologies.notificationmanager.data.storage.SessionStorage
import com.dynamictecnologies.notificationmanager.domain.entities.User

/**
 * Data Source local para operaciones de sesión y caché.
 * 
 * - Clean Architecture: Pertenece a la capa de datos
 */
class LocalAuthDataSource(
    private val sessionStorage: SessionStorage
) {
    
    /**
     * Guarda la sesión del usuario
     */
    fun saveSession(user: User, sessionDurationHours: Long = 24L) {
        sessionStorage.saveSession(user, sessionDurationHours)
    }
    
    /**
     * Limpia la sesión actual
     */
    fun clearSession() {
        sessionStorage.clearSession()
    }
    
    /**
     * Verifica si la sesión es válida
     */
    fun isSessionValid(): Boolean {
        return sessionStorage.isSessionValid()
    }
    
    /**
     * Verifica si hay una sesión guardada
     */
    fun hasSession(): Boolean {
        return sessionStorage.hasSession()
    }
    
    /**
     * Obtiene el ID del usuario guardado
     */
    fun getUserId(): String? {
        return sessionStorage.getUserId()
    }
    
    /**
     * Extiende la sesión actual
     */
    fun extendSession(additionalHours: Long = 24L) {
        sessionStorage.extendSession(additionalHours)
    }
}
