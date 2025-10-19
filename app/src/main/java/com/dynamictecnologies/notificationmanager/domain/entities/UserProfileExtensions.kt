package com.dynamictecnologies.notificationmanager.domain.entities

/**
 * Extensiones para UserProfile para facilitar la interoperabilidad con código legacy.
 * Permite convertir entre UserProfile (domain) y UserInfo (data) sin modificar UI existente.
 * 
 * Principios aplicados:
 * - DRY: Centraliza conversiones
 * - OCP: Extiende funcionalidad sin modificar las clases originales
 */

/**
 * Convierte UserProfile a UserInfo para compatibilidad con UI existente
 */
fun UserProfile.toUserInfo(): User {
    return User(
        id = this.uid,
        username = this.username,
        email = this.email,
        createdAt = this.createdAt
    )
}

/**
 * Convierte UserInfo a UserProfile
 */
fun User.toUserProfile(): UserProfile {
    return UserProfile(
        uid = this.id,
        username = this.username,
        email = this.email ?: "",
        createdAt = this.createdAt
    )
}
