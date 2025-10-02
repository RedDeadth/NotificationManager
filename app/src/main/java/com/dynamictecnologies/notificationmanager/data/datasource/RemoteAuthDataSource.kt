package com.dynamictecnologies.notificationmanager.data.datasource

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Data Source remoto para operaciones de autenticación con Firebase.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja comunicación con Firebase Auth
 * - DIP: Puede ser inyectado como dependencia
 * - Clean Architecture: Pertenece a la capa de datos
 */
class RemoteAuthDataSource(
    private val firebaseAuth: FirebaseAuth
) {
    
    /**
     * Obtiene el usuario actual de Firebase como Flow reactivo
     */
    fun getCurrentUser(): Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        
        firebaseAuth.addAuthStateListener(authStateListener)
        
        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }
    
    /**
     * Inicia sesión con email y contraseña
     */
    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: throw IllegalStateException("Usuario no disponible después de la autenticación")
    }
    
    /**
     * Registra un nuevo usuario con email y contraseña
     */
    suspend fun registerWithEmail(email: String, password: String): FirebaseUser {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        return result.user ?: throw IllegalStateException("Usuario no disponible después del registro")
    }
    
    /**
     * Inicia sesión con Google usando idToken
     */
    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        return result.user ?: throw IllegalStateException("Usuario no disponible después de autenticación con Google")
    }
    
    /**
     * Cierra la sesión en Firebase
     */
    suspend fun signOut() {
        firebaseAuth.signOut()
    }
    
    /**
     * Obtiene el usuario actual de forma síncrona
     */
    fun getCurrentUserSync(): FirebaseUser? {
        return firebaseAuth.currentUser
    }
}
