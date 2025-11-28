package com.dynamictecnologies.notificationmanager.util.logging

/**
 * Enmascarador de datos sensibles para prevenir filtración de información en logs.
 * 
 * Principios aplicados:
 * - SRP: Solo enmascara datos sensibles
 * - Security by Design: Protección de datos por defecto
 */
class SensitiveDataMasker {
    
    /**
     * Enmascara un email mostrando solo los primeros 2 caracteres
     * Ejemplo: "user@example.com" -> "us***@example.com"
     */
    fun maskEmail(email: String): String {
        if (email.isBlank()) return "***"
        
        val parts = email.split("@")
        if (parts.size != 2) return "***"
        
        val username = parts[0]
        val domain = parts[1]
        
        val maskedUsername = when {
            username.length <= 1 -> "***"
            username.length == 2 -> username.take(1) + "***"
            else -> username.take(2) + "***"
        }
        
        return "$maskedUsername@$domain"
    }
    
    /**
     * Enmascara un UID mostrando solo los primeros y últimos 3 caracteres
     * Ejemplo: "abc1234567xyz" -> "abc***xyz"
     */
    fun maskUserId(userId: String): String {
        return when {
            userId.isBlank() -> "***"
            userId.length <= 6 -> "***"
            else -> userId.take(3) + "***" + userId.takeLast(3)
        }
    }
    
    /**
     * Enmascara un username mostrando solo los primeros 2 caracteres
     * Ejemplo: "johndoe" -> "jo***"
     */
    fun maskUsername(username: String): String {
        return when {
            username.isBlank() -> "***"
            username.length <= 2 -> "***"
            else -> username.take(2) + "***"
        }
    }
    
    /**
     * Enmascara completamente un token o contraseña
     */
    fun maskToken(token: String): String {
        return if (token.isBlank()) "***" else "***TOKEN***"
    }
}
