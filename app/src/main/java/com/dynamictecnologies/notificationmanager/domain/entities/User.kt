package com.dynamictecnologies.notificationmanager.domain.entities

/**
 * Entidad de dominio que representa un usuario.
 * No depende de ninguna implementación externa (Firebase, etc.)
 * 
 * Principios aplicados:
 * - Clean Architecture: Entidad pura del dominio sin dependencias externas
 * - SRP: Solo representa los datos del usuario
 */
data class User(
    val id: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String? = null,
    val isEmailVerified: Boolean = false
)
