package com.dynamictecnologies.notificationmanager.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dynamictecnologies.notificationmanager.domain.entities.User
import java.util.concurrent.TimeUnit

/**
 * Implementación segura de SessionStorage usando EncryptedSharedPreferences.
 * 
 * Mejoras de seguridad:
 * - Encriptación AES256-GCM para valores
 * - Encriptación AES256-SIV para claves
 * - MasterKey gestionado por Android Keystore
 * 
 * - Security by Design: Encriptación por defecto
 */
class SecureSessionStorage(context: Context) : SessionStorage {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    override fun saveSession(user: User, sessionDurationHours: Long) {
        val expirationTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(sessionDurationHours)
        
        encryptedPrefs.edit().apply {
            putString(KEY_USER_ID, user.id)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_DISPLAY_NAME, user.displayName)
            putBoolean(KEY_EMAIL_VERIFIED, user.isEmailVerified)
            putLong(KEY_SESSION_EXPIRATION, expirationTime)
            putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            apply()
        }
    }
    
    override fun clearSession() {
        encryptedPrefs.edit().clear().apply()
    }
    
    override fun isSessionValid(): Boolean {
        val expiration = encryptedPrefs.getLong(KEY_SESSION_EXPIRATION, 0)
        return expiration > System.currentTimeMillis()
    }
    
    override fun getUserId(): String? {
        return encryptedPrefs.getString(KEY_USER_ID, null)
    }
    
    override fun getUserEmail(): String? {
        return encryptedPrefs.getString(KEY_USER_EMAIL, null)
    }
    
    override fun getUserDisplayName(): String? {
        return encryptedPrefs.getString(KEY_USER_DISPLAY_NAME, null)
    }
    
    override fun getLastLoginTime(): Long {
        return encryptedPrefs.getLong(KEY_LAST_LOGIN, 0)
    }
    
    override fun getRemainingSessionTime(): Long {
        val expiration = encryptedPrefs.getLong(KEY_SESSION_EXPIRATION, 0)
        val remaining = expiration - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }
    
    override fun extendSession(additionalHours: Long) {
        if (isSessionValid()) {
            val newExpiration = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(additionalHours)
            encryptedPrefs.edit().putLong(KEY_SESSION_EXPIRATION, newExpiration).apply()
        }
    }
    
    override fun hasSession(): Boolean {
        return getUserId() != null
    }
    
    companion object {
        private const val PREFS_NAME = "secure_auth_session_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_DISPLAY_NAME = "user_display_name"
        private const val KEY_EMAIL_VERIFIED = "email_verified"
        private const val KEY_SESSION_EXPIRATION = "session_expiration"
        private const val KEY_LAST_LOGIN = "last_login"
    }
}
