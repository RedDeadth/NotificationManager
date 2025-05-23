package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Patterns
import com.dynamictecnologies.notificationmanager.R
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.dynamictecnologies.notificationmanager.data.exceptions.toAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IAuthRepository {


    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    
    // Inicialización de Firebase Auth
    private val firebaseAuth: FirebaseAuth = Firebase.auth
    
    // Inicialización de SharedPreferences
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "auth_prefs",
        Context.MODE_PRIVATE
    )

    override val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        
        firebaseAuth.addAuthStateListener(authStateListener)
        
        // Limpieza cuando el flujo se cancele
        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }.flowOn(dispatcher)

    override suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return withContext(dispatcher) {
            try {
                validateEmail(email)
                validatePassword(password)
                
                val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val user = result.user ?: return@withContext Result.failure(
                    AuthException(AuthErrorCode.UNKNOWN_ERROR, "Error al iniciar sesión")
                )
                
                saveUserSession(user)
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e.toAuthException())
            }
        }
    }

    override suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> {
        return withContext(dispatcher) {
            try {
                validateEmail(email)
                validatePassword(password)
                
                val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: return@withContext Result.failure(
                    AuthException(AuthErrorCode.UNKNOWN_ERROR, "Error al registrar el usuario")
                )
                
                saveUserSession(user)
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e.toAuthException())
            }
        }
    }

    override suspend fun handleGoogleSignIn(idToken: String): Result<FirebaseUser> {
        return withContext(dispatcher) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = firebaseAuth.signInWithCredential(credential).await()
                val user = result.user ?: return@withContext Result.failure(
                    AuthException(AuthErrorCode.UNKNOWN_ERROR, "Error al iniciar sesión con Google")
                )
                
                saveUserSession(user)
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e.toAuthException())
            }
        }
    }

    override fun getGoogleSignInIntent(): Flow<Intent> = flow {
        emit(googleSignInClient.signInIntent)
    }.flowOn(dispatcher)

    override suspend fun signOut(): Result<Unit> {
        return withContext(dispatcher) {
            try {
                firebaseAuth.signOut()
                googleSignInClient.signOut()
                clearUserSession()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e.toAuthException())
            }
        }
    }

    override suspend fun saveUserSession(user: FirebaseUser): Result<Unit> {
        return withContext(dispatcher) {
            try {
                prefs.edit()
                    .putString("user_id", user.uid)
                    .putString("user_email", user.email)
                    .putLong("session_expiration", System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24))
                    .apply()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e.toAuthException())
            }
        }
    }

    override suspend fun clearUserSession(): Result<Unit> {
        return withContext(dispatcher) {
            try {
                prefs.edit().clear().apply()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e.toAuthException())
            }
        }
    }

    override suspend fun isSessionValid(): Boolean {
        return withContext(dispatcher) {
            val expiration = prefs.getLong("session_expiration", 0)
            expiration > System.currentTimeMillis()
        }
    }

    override suspend fun isUserAuthenticated(): Boolean {
        return withContext(dispatcher) {
            firebaseAuth.currentUser != null && isSessionValid()
        }
    }

    override suspend fun getCurrentUserId(): String? {
        return withContext(dispatcher) {
            firebaseAuth.currentUser?.uid
        }
    }

    override suspend fun refreshToken(): Result<String> {
        return withContext(dispatcher) {
            try {
                val user = firebaseAuth.currentUser
                if (user == null) {
                    Result.failure(AuthException(AuthErrorCode.UNAUTHORIZED, "Usuario no autenticado"))
                } else {
                    val tokenResult = user.getIdToken(true).await()
                    Result.success(tokenResult.token ?: "")
                }
            } catch (e: Exception) {
                Result.failure(e.toAuthException())
            }
        }
    }

    private suspend fun validateEmail(email: String) {
        if (email.isBlank()) {
            throw AuthException(AuthErrorCode.INVALID_EMAIL, "El email no puede estar vacío")
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            throw AuthException(AuthErrorCode.INVALID_EMAIL, "Formato de email inválido")
        }
    }

    private suspend fun validatePassword(password: String) {
        if (password.length < 8) {
            throw AuthException(AuthErrorCode.WEAK_PASSWORD, "La contraseña debe tener al menos 8 caracteres")
        }
        if (!password.any { it.isDigit() }) {
            throw AuthException(AuthErrorCode.WEAK_PASSWORD, "La contraseña debe contener al menos un número")
        }
        if (!password.any { it.isLetter() }) {
            throw AuthException(AuthErrorCode.WEAK_PASSWORD, "La contraseña debe contener al menos una letra")
        }
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}