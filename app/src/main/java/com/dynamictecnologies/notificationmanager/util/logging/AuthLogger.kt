package com.dynamictecnologies.notificationmanager.util.logging

import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException

/**
 * Logger especializado para operaciones de autenticación.
 * Proporciona logging estructurado y seguro para el módulo de auth.
 * 
 * - Security by Design: Enmascara datos sensibles automáticamente
 */
class AuthLogger(
    private val logger: Logger,
    private val masker: SensitiveDataMasker = SensitiveDataMasker()
) {
    companion object {
        private const val TAG = "Auth"
    }
    
    fun logSignInAttempt(email: String) {
        logger.i(TAG, "Intento de login: ${masker.maskEmail(email)}")
    }
    
    fun logSignInSuccess(userId: String, email: String) {
        logger.i(TAG, "Login exitoso - UID: ${masker.maskUserId(userId)}, Email: ${masker.maskEmail(email)}")
    }
    
    fun logSignInFailure(email: String, error: Throwable) {
        val errorCode = if (error is AuthException) error.code.name else "UNKNOWN"
        logger.w(
            TAG,
            "Login fallido - Email: ${masker.maskEmail(email)}, Error: $errorCode",
            error
        )
    }
    
    fun logRegistrationAttempt(email: String) {
        logger.i(TAG, "Intento de registro: ${masker.maskEmail(email)}")
    }
    
    fun logRegistrationSuccess(userId: String, email: String) {
        logger.i(TAG, "Registro exitoso - UID: ${masker.maskUserId(userId)}, Email: ${masker.maskEmail(email)}")
    }
    
    fun logRegistrationFailure(email: String, error: Throwable) {
        val errorCode = if (error is AuthException) error.code.name else "UNKNOWN"
        logger.w(
            TAG,
            "Registro fallido - Email: ${masker.maskEmail(email)}, Error: $errorCode",
            error
        )
    }
    
    fun logSignOut(userId: String) {
        logger.i(TAG, "Logout - UID: ${masker.maskUserId(userId)}")
    }
    
    fun logSignOutError(error: Throwable) {
        logger.e(TAG, "Error durante logout", error)
    }
    
    fun logSessionValidation(userId: String, isValid: Boolean) {
        logger.d(TAG, "Validación de sesión - UID: ${masker.maskUserId(userId)}, Válida: $isValid")
    }
    
    fun logGoogleSignInAttempt() {
        logger.i(TAG, "Intento de login con Google")
    }
    
    fun logGoogleSignInSuccess(userId: String, email: String) {
        logger.i(TAG, "Login con Google exitoso - UID: ${masker.maskUserId(userId)}, Email: ${masker.maskEmail(email)}")
    }
    
    fun logGoogleSignInFailure(error: Throwable) {
        logger.w(TAG, "Login con Google fallido", error)
    }
}
