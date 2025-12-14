package com.dynamictecnologies.notificationmanager.data.storage

import com.dynamictecnologies.notificationmanager.domain.entities.User

/**
 * Interfaz para el almacenamiento de sesiones.
 * 
 */
interface SessionStorage {
    /**
     * Guarda la sesión del usuario
     */
    fun saveSession(user: User, sessionDurationHours: Long = 24L)
    
    /**
     * Limpia la sesión actual
     */
    fun clearSession()
    
    /**
     * Verifica si la sesión es válida (no ha expirado)
     */
    fun isSessionValid(): Boolean
    
    /**
     * Obtiene el ID del usuario actual
     */
    fun getUserId(): String?
    
    /**
     * Obtiene el email del usuario actual
     */
    fun getUserEmail(): String?
    
    /**
     * Obtiene el nombre de display del usuario actual
     */
    fun getUserDisplayName(): String?
    
    /**
     * Obtiene el timestamp del último login
     */
    fun getLastLoginTime(): Long
    
    /**
     * Obtiene el tiempo restante de la sesión en milisegundos
     */
    fun getRemainingSessionTime(): Long
    
    /**
     * Extiende la sesión actual
     */
    fun extendSession(additionalHours: Long = 24L)
    
    /**
     * Verifica si hay una sesión guardada
     */
    fun hasSession(): Boolean
}
