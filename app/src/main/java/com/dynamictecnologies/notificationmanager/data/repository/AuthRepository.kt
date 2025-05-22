package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.dynamictecnologies.notificationmanager.R
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode
import com.dynamictecnologies.notificationmanager.service.UserService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import com.dynamictecnologies.notificationmanager.data.exceptions.toAuthException

class AuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userService: UserService
) : IAuthRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "auth_prefs",
        Context.MODE_PRIVATE
    )

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    override fun getCurrentUser(): Flow<FirebaseUser?> = flow {
        emit(auth.currentUser)
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            validateCredentials(email, password)
            val result = auth.signInWithEmailAndPassword(email, password).await()
            saveUserSession(result.user!!)
            Result.success(result.user!!)
        } catch (e: FirebaseAuthException) {
            Result.failure(e.toAuthException())
        }
    }

    override suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            validateCredentials(email, password)
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            saveUserSession(result.user!!)
            Result.success(result.user!!)
        } catch (e: FirebaseAuthException) {
            Result.failure(e.toAuthException())
        }
    }

    override fun getGoogleSignInIntent(): Flow<Intent> = flow {
        emit(googleSignInClient.signInIntent)
    }

    override suspend fun handleGoogleSignIn(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            saveUserSession(result.user!!)
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e.toAuthException())
        }
    }

    override fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            googleSignInClient.signOut()
            clearUserSession()
            userService.cleanup()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toAuthException())
        }
    }

    override fun saveUserSession(user: FirebaseUser): Result<Unit> {
        return try {
            prefs.edit().apply {
                putString("user_id", user.uid)
                putString("user_email", user.email)
                putLong("session_expiration", System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24))
                apply()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toAuthException())
        }
    }

    override fun clearUserSession(): Result<Unit> {
        return try {
            prefs.edit().clear().apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toAuthException())
        }
    }

    override fun isSessionValid(): Boolean {
        val expiration = prefs.getLong("session_expiration", 0)
        return expiration > System.currentTimeMillis()
    }

    private fun validateCredentials(email: String, password: String) {
        if (email.isBlank()) {
            throw AuthException(AuthErrorCode.INVALID_CREDENTIALS, "Email is required")
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            throw AuthException(AuthErrorCode.INVALID_CREDENTIALS, "Invalid email format")
        }
        if (password.length < 6) {
            throw AuthException(AuthErrorCode.WEAK_PASSWORD, "Password must be at least 6 characters")
        }
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}