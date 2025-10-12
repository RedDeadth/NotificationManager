package com.dynamictecnologies.notificationmanager.data.repository

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.datasource.LocalUserDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.RemoteUserDataSource
import com.dynamictecnologies.notificationmanager.data.mapper.UserProfileMapper
import com.dynamictecnologies.notificationmanager.data.validator.UsernameValidator
import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    private val firebaseAuth: FirebaseAuth
) : UserProfileRepository {
    
    companion object {
        private const val TAG = "UserProfileRepo"
    }
    
    override fun getUserProfile(): Flow<UserProfile?> {
        val currentUser = firebaseAuth.currentUser
        
        if (currentUser == null) {
            Log.d(TAG, "No hay usuario autenticado")
            return kotlinx.coroutines.flow.flowOf(null)
        }
        
        // Primero intentar devolver desde caché
        val cachedProfile = localDataSource.getProfile()
        if (cachedProfile != null && localDataSource.isCacheValid()) {
            Log.d(TAG, "Retornando perfil desde caché")
        }
        
        // Observar cambios remotos y actualizar caché
        return remoteDataSource.getUserProfileByUid(currentUser.uid)
            .map { userInfo ->
                userInfo?.let {
                    localDataSource.saveProfile(it)
                    UserProfileMapper.toDomain(it)
                }
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
                ?: return Result.failure(Exception("No hay usuario autenticado"))
            
            // Verificar si ya tiene perfil
            if (remoteDataSource.hasUserProfile(currentUser.uid)) {
                return Result.failure(Exception("Ya tienes un perfil registrado"))
            }
            
            // Verificar disponibilidad
            if (!remoteDataSource.isUsernameAvailable(username)) {
                return Result.failure(Exception("El nombre de usuario ya está en uso"))
            }
            
            // Registrar en Firebase
            remoteDataSource.registerUsername(
                uid = currentUser.uid,
                username = username,
                email = currentUser.email ?: ""
            )
            
            // Obtener el perfil recién creado
            val userInfo = remoteDataSource.getUserProfileSync(currentUser.uid)
                ?: return Result.failure(Exception("Error al obtener perfil creado"))
            
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
                ?: return Result.failure(Exception("No hay usuario autenticado"))
            
            // Invalidar caché
            localDataSource.invalidateCache()
            
            // Obtener desde remoto
            val userInfo = remoteDataSource.getUserProfileSync(currentUser.uid)
                ?: return Result.failure(Exception("Perfil no encontrado"))
            
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
