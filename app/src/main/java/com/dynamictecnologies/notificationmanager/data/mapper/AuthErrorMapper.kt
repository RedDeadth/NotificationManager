package com.dynamictecnologies.notificationmanager.data.mapper

import com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.google.firebase.auth.FirebaseAuthException

/**
 * Mapeador de errores de autenticación siguiendo el principio de responsabilidad única (SRP).
 * Esta clase solo se encarga de convertir excepciones de Firebase a excepciones de dominio.
 */
class AuthErrorMapper {
    
    /**
     * Mapea una excepción de FirebaseAuth a AuthException
     */
    fun mapFirebaseException(exception: FirebaseAuthException): AuthException {
        val errorCode = when (exception.errorCode) {
            "ERROR_INVALID_EMAIL" -> AuthErrorCode.INVALID_CREDENTIALS
            "ERROR_WRONG_PASSWORD" -> AuthErrorCode.INVALID_CREDENTIALS
            "ERROR_USER_NOT_FOUND" -> AuthErrorCode.USER_NOT_FOUND
            "ERROR_WEAK_PASSWORD" -> AuthErrorCode.WEAK_PASSWORD
            "ERROR_EMAIL_ALREADY_IN_USE" -> AuthErrorCode.EMAIL_ALREADY_IN_USE
            "ERROR_NETWORK_REQUEST_FAILED" -> AuthErrorCode.NETWORK_ERROR
            "ERROR_INVALID_CREDENTIAL" -> AuthErrorCode.INVALID_CREDENTIALS
            "ERROR_USER_DISABLED" -> AuthErrorCode.USER_NOT_FOUND
            "ERROR_TOO_MANY_REQUESTS" -> AuthErrorCode.NETWORK_ERROR
            "ERROR_OPERATION_NOT_ALLOWED" -> AuthErrorCode.UNKNOWN_ERROR
            "ERROR_INVALID_CUSTOM_TOKEN" -> AuthErrorCode.INVALID_TOKEN
            else -> AuthErrorCode.UNKNOWN_ERROR
        }
        
        return AuthException(
            code = errorCode,
            message = exception.message,
            cause = exception
        )
    }
    
    /**
     * Mapea cualquier excepción a AuthException
     */
    fun mapException(exception: Throwable): AuthException {
        return when (exception) {
            is AuthException -> exception
            is FirebaseAuthException -> mapFirebaseException(exception)
            else -> AuthException(
                code = AuthErrorCode.UNKNOWN_ERROR,
                message = exception.message ?: "Error desconocido",
                cause = exception
            )
        }
    }
    
    /**
     * Obtiene el mensaje de error localizado para el usuario
     */
    fun getLocalizedErrorMessage(exception: AuthException): String {
        return when (exception.code) {
            AuthErrorCode.INVALID_CREDENTIALS -> "Credenciales inválidas. Verifica tu email y contraseña"
            AuthErrorCode.USER_NOT_FOUND -> "Usuario no encontrado. Verifica tus credenciales"
            AuthErrorCode.WEAK_PASSWORD -> "La contraseña es muy débil. Usa al menos 6 caracteres"
            AuthErrorCode.EMAIL_ALREADY_IN_USE -> "Este email ya está registrado. Intenta iniciar sesión"
            AuthErrorCode.NETWORK_ERROR -> "Error de conexión. Verifica tu internet"
            AuthErrorCode.SESSION_EXPIRED -> "Tu sesión ha expirado. Inicia sesión nuevamente"
            AuthErrorCode.INVALID_TOKEN -> "Token de autenticación inválido"
            AuthErrorCode.UNKNOWN_ERROR -> exception.message ?: "Ha ocurrido un error inesperado"
        }
    }
}
