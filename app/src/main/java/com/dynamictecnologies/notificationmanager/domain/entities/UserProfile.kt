package com.dynamictecnologies.notificationmanager.domain.entities

/**
 * Entidad de dominio que representa el perfil de usuario en la aplicación.
 * Separada de User (autenticación) para seguir SRP.
 * 
 * Principios aplicados:
 * - Clean Architecture: Entidad pura sin dependencias externas
 * - SRP: Solo representa datos del perfil de usuario
 * - ISP: No hereda de User, son conceptos separados
 */
data class UserProfile(
    val uid: String,
    val username: String,
    val email: String,
    val createdAt: Long,
    val isActive: Boolean = true
) {
    /**
     * Valida si el perfil es válido
     */
    fun isValid(): Boolean {
        return uid.isNotBlank() && 
               username.isNotBlank() && 
               email.isNotBlank()
    }
}
