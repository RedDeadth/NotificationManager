package com.dynamictecnologies.notificationmanager.domain.repositories

import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del repositorio de autenticación en la capa de dominio.
 * No tiene dependencias de Android ni de Firebase.
 * 
 * Principios aplicados:
 * - DIP: Abstracción que no depende de detalles de implementación
 * - ISP: Interfaz segregada con operaciones específicas de autenticación
 * - Clean Architecture: Pertenece a la capa de dominio, sin conocer detalles de infraestructura
 */
interface AuthRepository {
    /**
     * Obtiene el usuario actual autenticado como Flow reactivo
     */
    fun getCurrentUser(): Flow<User?>

    /**
     * Inicia sesión con email y contraseña
     */
    suspend fun signInWithEmail(email: String, password: String): Result<User>

    /**
     * Registra un nuevo usuario con email y contraseña
     */
    suspend fun registerWithEmail(email: String, password: String): Result<User>

    /**
     * Inicia sesión con Google usando el idToken
     */
    suspend fun signInWithGoogle(idToken: String): Result<User>

    /**
     * Cierra la sesión del usuario actual
     */
    suspend fun signOut(): Result<Unit>

    /**
     * Verifica si hay una sesión válida
     */
    suspend fun isSessionValid(): Boolean

    /**
     * Estado de autenticación de Firebase observable
     */
    fun getFirebaseAuthState(): Flow<FirebaseUser?>

    /**
     * Espera hasta que el usuario esté completamente sincronizado
     */
    suspend fun awaitFirebaseUser(): FirebaseUser?
}