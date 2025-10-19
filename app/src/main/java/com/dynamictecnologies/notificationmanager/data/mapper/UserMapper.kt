package com.dynamictecnologies.notificationmanager.data.mapper

import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.google.firebase.auth.FirebaseUser

/**
 * Mapper para convertir entre FirebaseUser y User del dominio.
 * 
 * Principios aplicados:
 * - SRP: Solo se encarga de mapear entidades
 * - Clean Architecture: Convierte detalles de implementación a entidades de dominio
 */
object UserMapper {
    
    /**
     * Convierte un FirebaseUser a User del dominio
     */
    fun toDomain(firebaseUser: FirebaseUser): User {
        return User(
            id = firebaseUser.uid,
            email = firebaseUser.email,
            displayName = firebaseUser.displayName,

            isEmailVerified = firebaseUser.isEmailVerified
        )
    }
}
