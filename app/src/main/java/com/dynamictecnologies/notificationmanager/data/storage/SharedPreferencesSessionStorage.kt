package com.dynamictecnologies.notificationmanager.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.dynamictecnologies.notificationmanager.domain.entities.User
import java.util.concurrent.TimeUnit

/**
 * Implementación de SessionStorage usando SharedPreferences.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja el almacenamiento de sesiones en SharedPreferences
 * - DIP: Implementa la abstracción SessionStorage
 */
class SharedPreferencesSessionStorage(context: Context) : SessionStorage {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    override fun saveSession(user: User, sessionDurationHours: Long) {
        val expirationTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(sessionDurationHours)
        
        prefs.edit().apply {
            putString(KEY_USER_ID, user.id)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_DISPLAY_NAME, user.displayName)
            putString(KEY_USER_PHOTO_URL, user.photoUrl)
            putBoolean(KEY_EMAIL_VERIFIED, user.isEmailVerified)
            putLong(KEY_SESSION_EXPIRATION, expirationTime)
            putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            apply()
        }
    }
    
    override fun clearSession() {
        prefs.edit().clear().apply()
    }
    
    override fun isSessionValid(): Boolean {
        val expiration = prefs.getLong(KEY_SESSION_EXPIRATION, 0)
        return expiration > System.currentTimeMillis()
    }
    
    override fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }
    
    override fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }
    
    override fun getUserDisplayName(): String? {
        return prefs.getString(KEY_USER_DISPLAY_NAME, null)
    }
    
    override fun getLastLoginTime(): Long {
        return prefs.getLong(KEY_LAST_LOGIN, 0)
    }
    
    override fun getRemainingSessionTime(): Long {
        val expiration = prefs.getLong(KEY_SESSION_EXPIRATION, 0)
        val remaining = expiration - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }
    
    override fun extendSession(additionalHours: Long) {
        if (isSessionValid()) {
            val newExpiration = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(additionalHours)
            prefs.edit().putLong(KEY_SESSION_EXPIRATION, newExpiration).apply()
        }
    }
    
    override fun hasSession(): Boolean {
        return getUserId() != null
    }
    
    companion object {
        private const val PREFS_NAME = "auth_session_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_DISPLAY_NAME = "user_display_name"
        private const val KEY_USER_PHOTO_URL = "user_photo_url"
        private const val KEY_EMAIL_VERIFIED = "email_verified"
        private const val KEY_SESSION_EXPIRATION = "session_expiration"
        private const val KEY_LAST_LOGIN = "last_login"
    }
}
