package com.dynamictecnologies.notificationmanager.domain.entities

/**
 * Entidad de dominio que representa un usuario.
 * No depende de ninguna implementación externa (Firebase, etc.)
 * 
 * Principios aplicados:
 * - Clean Architecture: Entidad pura del dominio sin dependencias externas
 * - SRP: Solo representa los datos del usuario
 */
// domain/entities/User.kt
data class User(
    val id: String,
    val username: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val isEmailVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sharedWith: Map<String, String> = emptyMap(),
    val isShared: Boolean = false,
    val addedAt: Long = 0L
)