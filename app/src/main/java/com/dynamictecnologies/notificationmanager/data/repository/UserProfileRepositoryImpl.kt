package com.dynamictecnologies.notificationmanager.data.repository

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.constants.AuthStrings
import com.dynamictecnologies.notificationmanager.data.datasource.LocalUserDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.RemoteUserDataSource
import com.dynamictecnologies.notificationmanager.data.mapper.UserProfileMapper
import com.dynamictecnologies.notificationmanager.data.validator.UsernameValidator
import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.first
/**
 * Implementación del repositorio de perfiles de usuario.
 * Coordina entre data sources local y remoto.
 * 
 * Principios aplicados:
 * - SRP: Solo coordina entre data sources, no tiene lógica de negocio compleja
 * - DIP: Depende de abstracciones (inyección de datasources)
 * - DRY: Reutiliza validator y mappers
 * - Clean Architecture: Implementación en capa de datos
 * - Repository Pattern: Fuente única de verdad para perfiles
 */
class UserProfileRepositoryImpl(
    private val remoteDataSource: RemoteUserDataSource,
    private val localDataSource: LocalUserDataSource,
    private val usernameValidator: UsernameValidator,
    private val firebaseAuth: FirebaseAuth,
    private val authRepository: AuthRepository
) : UserProfileRepository {
    
    companion object {
        private const val TAG = "UserProfileRepo"
    }

    override fun getUserProfile(): Flow<UserProfile?> {
        return authRepository.getFirebaseAuthState()
            .map { firebaseUser ->
                if (firebaseUser == null) {
                    Log.d(TAG, "No hay usuario autenticado")
                    return@map null
                }

                val cachedProfile = localDataSource.getProfile()
                if (cachedProfile != null && localDataSource.isCacheValid()) {
                    Log.d(TAG, "Retornando perfil desde caché")
                    return@map UserProfileMapper.toDomain(cachedProfile)
                }

                try {
                    val userInfoFlow = remoteDataSource.getUserProfileByUid(firebaseUser.uid)
                    val userInfo = userInfoFlow.first()
                    userInfo?.let {
                        localDataSource.saveProfile(it)
                        return@map UserProfileMapper.toDomain(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo perfil remoto: ${e.message}")
                }

                cachedProfile?.let {
                    Log.d(TAG, "Fallback a caché por error remoto")
                    return@map UserProfileMapper.toDomain(it)
                }

                null
            }
    }
    
    override suspend fun registerUsername(username: String): Result<UserProfile> {
        return try {
            // Validar username
            val validationResult = usernameValidator.validate(username)
            if (validationResult is UsernameValidator.ValidationResult.Invalid) {
                val errorMsg = usernameValidator.getErrorMessage(validationResult.error)
                return Result.failure(Exception(errorMsg))
            }
            
            // Verificar usuario autenticado
            val currentUser = firebaseAuth.currentUser 
                ?: return Result.failure(Exception(AuthStrings.OperationErrors.NO_AUTHENTICATED_USER))
            
            // Verificar si ya tiene perfil
            if (remoteDataSource.hasUserProfile(currentUser.uid)) {
                return Result.failure(Exception(AuthStrings.OperationErrors.PROFILE_ALREADY_EXISTS))
            }
            
            // Verificar disponibilidad
            if (!remoteDataSource.isUsernameAvailable(username)) {
                return Result.failure(Exception(AuthStrings.OperationErrors.USERNAME_ALREADY_IN_USE))
            }
            
            // Registrar en Firebase
            remoteDataSource.registerUsername(
                uid = currentUser.uid,
                username = username,
                email = currentUser.email ?: ""
            )
            
            // Obtener el perfil recién creado
            val userInfo = remoteDataSource.getUserProfileSync(currentUser.uid)
                ?: return Result.failure(Exception(AuthStrings.OperationErrors.PROFILE_CREATION_FAILED))
            
            // Guardar en caché
            localDataSource.saveProfile(userInfo)
            
            val userProfile = UserProfileMapper.toDomain(userInfo)
            Log.d(TAG, "Username registrado exitosamente: $username")
            
            Result.success(userProfile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando username: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun isUsernameAvailable(username: String): Boolean {
        return try {
            remoteDataSource.isUsernameAvailable(username)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando disponibilidad: ${e.message}")
            false
        }
    }
    
    override suspend fun hasUserProfile(): Boolean {
        val currentUser = firebaseAuth.currentUser ?: return false
        
        return try {
            remoteDataSource.hasUserProfile(currentUser.uid)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando perfil: ${e.message}")
            false
        }
    }
    
    override suspend fun refreshProfile(): Result<UserProfile> {
        return try {
            val currentUser = firebaseAuth.currentUser 
                ?: return Result.failure(Exception(AuthStrings.OperationErrors.NO_AUTHENTICATED_USER))
            
            // Invalidar caché
            localDataSource.invalidateCache()
            
            // Obtener desde remoto
            val userInfo = remoteDataSource.getUserProfileSync(currentUser.uid)
                ?: return Result.failure(Exception(AuthStrings.OperationErrors.PROFILE_NOT_FOUND))
            
            // Guardar en caché
            localDataSource.saveProfile(userInfo)
            
            val userProfile = UserProfileMapper.toDomain(userInfo)
            Result.success(userProfile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error refrescando perfil: ${e.message}", e)
            
            // Intentar retornar desde caché como fallback
            localDataSource.getProfile()?.let {
                return Result.success(UserProfileMapper.toDomain(it))
            }
            
            Result.failure(e)
        }
    }
    
    override fun clearCache() {
        localDataSource.clearCache()
    }
}
