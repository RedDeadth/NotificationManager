package com.dynamictecnologies.notificationmanager.data.exceptions

import com.google.firebase.auth.FirebaseAuthException

/**
 * Excepción personalizada para errores de autenticación
 */
class AuthException(
    val code: AuthErrorCode,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Códigos de error de autenticación
 */
enum class AuthErrorCode {
    INVALID_CREDENTIALS,
    USER_NOT_FOUND,
    WEAK_PASSWORD,
    EMAIL_ALREADY_IN_USE,
    NETWORK_ERROR,
    UNKNOWN_ERROR,
    INVALID_TOKEN,
    SESSION_EXPIRED
}

/**
 * Función de extensión mejorada para convertir excepciones a AuthException
 */
fun Throwable.toAuthException(): AuthException {
    return when (this) {
        is AuthException -> this
        is FirebaseAuthException -> {
            val errorCode = when (this.errorCode) {
                "ERROR_INVALID_EMAIL" -> AuthErrorCode.INVALID_CREDENTIALS
                "ERROR_WRONG_PASSWORD" -> AuthErrorCode.INVALID_CREDENTIALS
                "ERROR_USER_NOT_FOUND" -> AuthErrorCode.USER_NOT_FOUND
                "ERROR_WEAK_PASSWORD" -> AuthErrorCode.WEAK_PASSWORD
                "ERROR_EMAIL_ALREADY_IN_USE" -> AuthErrorCode.EMAIL_ALREADY_IN_USE
                "ERROR_NETWORK_REQUEST_FAILED" -> AuthErrorCode.NETWORK_ERROR
                "ERROR_INVALID_CREDENTIAL" -> AuthErrorCode.INVALID_CREDENTIALS
                "ERROR_USER_DISABLED" -> AuthErrorCode.USER_NOT_FOUND
                "ERROR_TOO_MANY_REQUESTS" -> AuthErrorCode.NETWORK_ERROR
                "ERROR_INVALID_CUSTOM_TOKEN" -> AuthErrorCode.INVALID_TOKEN
                else -> AuthErrorCode.UNKNOWN_ERROR
            }
            AuthException(code = errorCode, message = this.message, cause = this)
        }
        else -> AuthException(
            code = AuthErrorCode.UNKNOWN_ERROR,
            message = this.message,
            cause = this
        )
    }
}
