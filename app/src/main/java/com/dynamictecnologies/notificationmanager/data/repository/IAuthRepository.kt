package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

interface IAuthRepository {
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun handleGoogleSignIn(idToken: String): Result<FirebaseUser>
    fun signOut(): Result<Unit>
    fun getCurrentUser(): Flow<FirebaseUser?>
    fun getGoogleSignInIntent(): Flow<Intent>
    fun saveUserSession(user: FirebaseUser): Result<Unit>
    fun clearUserSession(): Result<Unit>
    fun isSessionValid(): Boolean
}
