package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.content.Intent
import com.dynamictecnologies.notificationmanager.R
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.dynamictecnologies.notificationmanager.data.exceptions.toAuthException
import com.dynamictecnologies.notificationmanager.data.mapper.AuthErrorMapper
import com.dynamictecnologies.notificationmanager.data.session.SessionManager
import com.dynamictecnologies.notificationmanager.data.validator.AuthValidator
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

/**
 * Repositorio de autenticación que implementa el patrón Repository y sigue principios SOLID.
 * 
 * Principios aplicados:
 * - SRP: Delega validación a AuthValidator, mapeo de errores a AuthErrorMapper y sesión a SessionManager
 * - OCP: Extensible para nuevos métodos de autenticación
 * - DIP: Depende de abstracciones (IAuthRepository, FirebaseAuth)
 */
class AuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userService: UserService,
    private val validator: AuthValidator = AuthValidator(),
    private val errorMapper: AuthErrorMapper = AuthErrorMapper(),
    private val sessionManager: SessionManager = SessionManager(context)
) : IAuthRepository {

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
            // Validar credenciales antes de hacer la llamada a Firebase
            val validationResult = validator.validateLoginCredentials(email, password)
            if (validationResult is AuthValidator.ValidationResult.Invalid) {
                return Result.failure(
                    AuthException(
                        code = when (validationResult.error) {
                            AuthValidator.ValidationError.EMPTY_EMAIL,
                            AuthValidator.ValidationError.INVALID_EMAIL_FORMAT -> 
                                com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.INVALID_CREDENTIALS
                            AuthValidator.ValidationError.EMPTY_PASSWORD,
                            AuthValidator.ValidationError.WEAK_PASSWORD -> 
                                com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.WEAK_PASSWORD
                            else -> com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.UNKNOWN_ERROR
                        },
                        message = validator.getErrorMessage(validationResult.error)
                    )
                )
            }
            
            // Autenticar con Firebase
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw AuthException(
                code = com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.UNKNOWN_ERROR,
                message = "Usuario no disponible después de la autenticación"
            )
            
            // Guardar sesión
            saveUserSession(user)
            Result.success(user)
        } catch (e: FirebaseAuthException) {
            Result.failure(errorMapper.mapFirebaseException(e))
        } catch (e: AuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(errorMapper.mapException(e))
        }
    }

    override suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            // Validar credenciales antes de crear la cuenta
            val validationResult = validator.validateLoginCredentials(email, password)
            if (validationResult is AuthValidator.ValidationResult.Invalid) {
                return Result.failure(
                    AuthException(
                        code = when (validationResult.error) {
                            AuthValidator.ValidationError.EMPTY_EMAIL,
                            AuthValidator.ValidationError.INVALID_EMAIL_FORMAT -> 
                                com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.INVALID_CREDENTIALS
                            AuthValidator.ValidationError.EMPTY_PASSWORD,
                            AuthValidator.ValidationError.WEAK_PASSWORD -> 
                                com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.WEAK_PASSWORD
                            else -> com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.UNKNOWN_ERROR
                        },
                        message = validator.getErrorMessage(validationResult.error)
                    )
                )
            }
            
            // Crear cuenta en Firebase
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw AuthException(
                code = com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.UNKNOWN_ERROR,
                message = "Usuario no disponible después del registro"
            )
            
            // Guardar sesión
            saveUserSession(user)
            Result.success(user)
        } catch (e: FirebaseAuthException) {
            Result.failure(errorMapper.mapFirebaseException(e))
        } catch (e: AuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(errorMapper.mapException(e))
        }
    }

    override fun getGoogleSignInIntent(): Flow<Intent> = flow {
        emit(googleSignInClient.signInIntent)
    }

    override suspend fun handleGoogleSignIn(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw AuthException(
                code = com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.UNKNOWN_ERROR,
                message = "Usuario no disponible después de autenticación con Google"
            )
            
            saveUserSession(user)
            Result.success(user)
        } catch (e: FirebaseAuthException) {
            Result.failure(errorMapper.mapFirebaseException(e))
        } catch (e: AuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(errorMapper.mapException(e))
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
            Result.failure(errorMapper.mapException(e))
        }
    }

    override fun saveUserSession(user: FirebaseUser): Result<Unit> {
        return try {
            sessionManager.saveSession(user)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(errorMapper.mapException(e))
        }
    }

    override fun clearUserSession(): Result<Unit> {
        return try {
            sessionManager.clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(errorMapper.mapException(e))
        }
    }

    override fun isSessionValid(): Boolean {
        return sessionManager.isSessionValid()
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}