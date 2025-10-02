package com.dynamictecnologies.notificationmanager.data.repository

import com.dynamictecnologies.notificationmanager.data.datasource.LocalAuthDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.RemoteAuthDataSource
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.dynamictecnologies.notificationmanager.data.mapper.AuthErrorMapper
import com.dynamictecnologies.notificationmanager.data.mapper.UserMapper
import com.dynamictecnologies.notificationmanager.data.validator.AuthValidator
import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import com.dynamictecnologies.notificationmanager.service.UserService
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación del repositorio de autenticación que coordina entre data sources.
 * 
 * Principios aplicados:
 * - SRP: Coordina entre data sources sin lógica de negocio compleja
 * - DIP: Todas las dependencias son inyectadas, sin valores por defecto
 * - DRY: Lógica común extraída a funciones privadas
 * - OCP: Extensible para nuevos métodos de autenticación
 * - Clean Architecture: Implementación en capa de datos, expone entidades de dominio
 */
class AuthRepositoryImpl(
    private val remoteDataSource: RemoteAuthDataSource,
    private val localDataSource: LocalAuthDataSource,
    private val validator: AuthValidator,
    private val errorMapper: AuthErrorMapper,
    private val userService: UserService
) : AuthRepository {

    override fun getCurrentUser(): Flow<User?> {
        return remoteDataSource.getCurrentUser().map { firebaseUser ->
            firebaseUser?.let { UserMapper.toDomain(it) }
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return executeAuthOperation(
            validationResult = validator.validateLoginCredentials(email, password)
        ) {
            val firebaseUser = remoteDataSource.signInWithEmail(email, password)
            val user = UserMapper.toDomain(firebaseUser)
            saveUserSession(user)
            user
        }
    }

    override suspend fun registerWithEmail(email: String, password: String): Result<User> {
        return executeAuthOperation(
            validationResult = validator.validateLoginCredentials(email, password)
        ) {
            val firebaseUser = remoteDataSource.registerWithEmail(email, password)
            val user = UserMapper.toDomain(firebaseUser)
            saveUserSession(user)
            user
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return executeAuthOperation {
            val firebaseUser = remoteDataSource.signInWithGoogle(idToken)
            val user = UserMapper.toDomain(firebaseUser)
            saveUserSession(user)
            user
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            remoteDataSource.signOut()
            localDataSource.clearSession()
            userService.cleanup()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(errorMapper.mapException(e))
        }
    }

    override suspend fun isSessionValid(): Boolean {
        return localDataSource.isSessionValid()
    }

    /**
     * Función privada que encapsula la lógica común de operaciones de autenticación.
     * Aplica principio DRY evitando código duplicado.
     */
    private suspend fun <T> executeAuthOperation(
        validationResult: AuthValidator.ValidationResult? = null,
        operation: suspend () -> T
    ): Result<T> {
        return try {
            // Validar si se proporciona validación
            validationResult?.let { result ->
                if (result is AuthValidator.ValidationResult.Invalid) {
                    return Result.failure(mapValidationError(result.error))
                }
            }
            
            // Ejecutar operación
            val data = operation()
            Result.success(data)
            
        } catch (e: FirebaseAuthException) {
            Result.failure(errorMapper.mapFirebaseException(e))
        } catch (e: AuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(errorMapper.mapException(e))
        }
    }

    /**
     * Mapea errores de validación a AuthException.
     * Aplica principio DRY evitando código duplicado.
     */
    private fun mapValidationError(error: AuthValidator.ValidationError): AuthException {
        val code = when (error) {
            AuthValidator.ValidationError.EMPTY_EMAIL,
            AuthValidator.ValidationError.INVALID_EMAIL_FORMAT -> 
                com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.INVALID_CREDENTIALS
            AuthValidator.ValidationError.EMPTY_PASSWORD,
            AuthValidator.ValidationError.WEAK_PASSWORD -> 
                com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.WEAK_PASSWORD
            AuthValidator.ValidationError.PASSWORDS_DO_NOT_MATCH ->
                com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode.INVALID_CREDENTIALS
        }
        
        return AuthException(
            code = code,
            message = validator.getErrorMessage(error)
        )
    }

    /**
     * Guarda la sesión del usuario en el almacenamiento local
     */
    private fun saveUserSession(user: User) {
        localDataSource.saveSession(user)
    }
}
