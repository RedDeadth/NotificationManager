package com.dynamictecnologies.notificationmanager.data.session

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseUser
import java.util.concurrent.TimeUnit

/**
 * Gestor de sesión siguiendo el principio de responsabilidad única (SRP).
 * Esta clase solo se encarga de gestionar el almacenamiento y validación de sesiones.
 */
class SessionManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    /**
     * Guarda la información de sesión del usuario
     */
    fun saveSession(user: FirebaseUser, sessionDurationHours: Long = DEFAULT_SESSION_HOURS) {
        val expirationTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(sessionDurationHours)
        
        prefs.edit().apply {
            putString(KEY_USER_ID, user.uid)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_DISPLAY_NAME, user.displayName)
            putLong(KEY_SESSION_EXPIRATION, expirationTime)
            putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            apply()
        }
    }
    
    /**
     * Limpia la sesión del usuario
     */
    fun clearSession() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Verifica si la sesión es válida (no ha expirado)
     */
    fun isSessionValid(): Boolean {
        val expiration = prefs.getLong(KEY_SESSION_EXPIRATION, 0)
        return expiration > System.currentTimeMillis()
    }
    
    /**
     * Obtiene el ID del usuario actual
     */
    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }
    
    /**
     * Obtiene el email del usuario actual
     */
    fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }
    
    /**
     * Obtiene el nombre de display del usuario actual
     */
    fun getUserDisplayName(): String? {
        return prefs.getString(KEY_USER_DISPLAY_NAME, null)
    }
    
    /**
     * Obtiene el timestamp del último login
     */
    fun getLastLoginTime(): Long {
        return prefs.getLong(KEY_LAST_LOGIN, 0)
    }
    
    /**
     * Obtiene el tiempo restante de la sesión en milisegundos
     */
    fun getRemainingSessionTime(): Long {
        val expiration = prefs.getLong(KEY_SESSION_EXPIRATION, 0)
        val remaining = expiration - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }
    
    /**
     * Extiende la sesión actual
     */
    fun extendSession(additionalHours: Long = DEFAULT_SESSION_HOURS) {
        if (isSessionValid()) {
            val newExpiration = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(additionalHours)
            prefs.edit().putLong(KEY_SESSION_EXPIRATION, newExpiration).apply()
        }
    }
    
    /**
     * Verifica si hay una sesión guardada (independiente de si expiró o no)
     */
    fun hasSession(): Boolean {
        return getUserId() != null
    }
    
    companion object {
        private const val PREFS_NAME = "auth_session_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_DISPLAY_NAME = "user_display_name"
        private const val KEY_SESSION_EXPIRATION = "session_expiration"
        private const val KEY_LAST_LOGIN = "last_login"
        private const val DEFAULT_SESSION_HOURS = 24L
    }
}
