package com.dynamictecnologies.notificationmanager.data.exceptions

class AuthException(
    val code: AuthErrorCode,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

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

fun Throwable.toAuthException(): AuthException {
    return when (this) {
        is AuthException -> this
        else -> AuthException(
            code = AuthErrorCode.UNKNOWN_ERROR,
            message = this.message,
            cause = this
        )
    }
}
