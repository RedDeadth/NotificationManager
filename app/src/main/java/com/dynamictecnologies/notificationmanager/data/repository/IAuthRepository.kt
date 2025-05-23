package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Intent
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

interface IAuthRepository {
    // Flujos de datos
    val currentUser: Flow<FirebaseUser?>
    
    // Operaciones de autenticación
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun handleGoogleSignIn(idToken: String): Result<FirebaseUser>
    suspend fun signOut(): Result<Unit>
    
    // Estado de la sesión
    suspend fun isUserAuthenticated(): Boolean
    suspend fun getCurrentUserId(): String?
    
    // Gestión de tokens
    suspend fun refreshToken(): Result<String>
    
    // Google SignIn
    fun getGoogleSignInIntent(): Flow<Intent>
    
    // Sesión
    suspend fun saveUserSession(user: FirebaseUser): Result<Unit>
    suspend fun clearUserSession(): Result<Unit>
    suspend fun isSessionValid(): Boolean
}