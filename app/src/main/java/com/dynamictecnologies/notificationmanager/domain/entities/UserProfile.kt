package com.dynamictecnologies.notificationmanager.domain.entities

/**
 * Entidad de dominio que representa el perfil de usuario en la aplicación.
 * Separada de User (autenticación) para seguir SRP.
 * 
 * - Clean Architecture: Entidad pura sin dependencias externas
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
