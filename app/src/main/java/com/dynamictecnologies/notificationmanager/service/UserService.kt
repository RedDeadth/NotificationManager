package com.dynamictecnologies.notificationmanager.service

import android.util.Log
import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/**
 * @deprecated Este servicio será eliminado en futuras versiones.
 * Usa UserProfileRepository + UseCases en su lugar.
 * 
 * Razón: Violaba Clean Architecture al mezclar lógica de negocio con acceso a datos.
 * Migración: Ver UserProfileRepository, RegisterUsernameUseCase, GetUserProfileUseCase
 */
@Deprecated(
    message = "Use UserProfileRepository con UseCases en su lugar",
    replaceWith = ReplaceWith(
        "UserProfileRepositoryImpl",
        "com.dynamictecnologies.notificationmanager.data.repository.UserProfileRepositoryImpl"
    ),
    level = DeprecationLevel.WARNING
)
class UserService(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val scope: CoroutineScope
) {
    private val usersRef = database.getReference("users")
    private val usernamesRef = database.getReference("usernames")

    private val _userProfileFlow = MutableStateFlow<User?>(null)
    val userProfileFlow = _userProfileFlow.asStateFlow()

    private var userListener: ValueEventListener? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    
    // Caché local para reducir consultas
    private var cachedUsername: String? = null
    private var cachedUserInfo: User? = null
    private var lastFetchTime: Long = 0
    private val CACHE_VALID_TIME = TimeUnit.MINUTES.toMillis(5) // 5 minutos

    init {
        // Habilitar persistencia offline para Firebase
        try {
            database.setPersistenceEnabled(true)
            // Mantener referencias clave sincronizadas
            usernamesRef.keepSynced(true)
        } catch (e: Exception) {
            Log.d("UserService", "Persistencia ya configurada: ${e.message}")
        }
        
        // Verificar estado inicial en una corrutina
        scope.launch {
            auth.currentUser?.let {
                try {
                    checkCurrentUserRegistration()
                } catch (e: Exception) {
                    Log.e("UserService", "Error en verificación inicial: ${e.message}")
                    _userProfileFlow.value = null
                }
            }
        }
        setupAuthListener()
    }

    private fun setupAuthListener() {
        authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user == null) {
                cleanup()
            } else {
                scope.launch {
                    try {
                        checkCurrentUserRegistration()
                    } catch (e: Exception) {
                        Log.e("UserService", "Error al verificar registro: ${e.message}")
                        _userProfileFlow.value = null
                    }
                }
            }
        }
        authStateListener?.let { auth.addAuthStateListener(it) }
    }

    suspend fun checkCurrentUserRegistration() {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.d("UserService", "No hay usuario autenticado")
                _userProfileFlow.value = null
                return
            }
            
            // Verificar caché primero
            val now = System.currentTimeMillis()
            if (cachedUserInfo != null && (now - lastFetchTime < CACHE_VALID_TIME)) {
                Log.d("UserService", "Usando perfil cacheado: ${cachedUserInfo?.username}")
                _userProfileFlow.value = cachedUserInfo
                return
            }

            Log.d("UserService", "Verificando registro para UID: ${currentUser.uid}")
            
            // Usar timeout para limitar tiempo de espera en red
            withTimeout(5000) {
                // Si tenemos username en caché, saltamos la primera consulta
                val username = if (cachedUsername != null) {
                    cachedUsername!!
                } else {
                    val snapshot = usernamesRef.orderByValue()
                        .equalTo(currentUser.uid)
                        .get()
                        .await()

                    if (!snapshot.exists()) {
                        Log.d("UserService", "No se encontró perfil para ${currentUser.uid}")
                        _userProfileFlow.value = null
                        return@withTimeout
                    }

                    val fetchedUsername = snapshot.children.firstOrNull()?.key
                    if (fetchedUsername == null) {
                        Log.e("UserService", "Username no encontrado para ${currentUser.uid}")
                        _userProfileFlow.value = null
                        return@withTimeout
                    }
                    
                    // Guardar en caché
                    cachedUsername = fetchedUsername
                    fetchedUsername
                }
                
                Log.d("UserService", "Perfil encontrado: $username")
                
                // Obtener los datos del perfil
                val userRef = usersRef.child(username)
                userRef.keepSynced(true)
                val userSnapshot = userRef.get().await()
                
                if (!userSnapshot.exists()) {
                    Log.e("UserService", "Datos de usuario no encontrados para $username")
                    _userProfileFlow.value = null
                    return@withTimeout
                }
                
                // Crear objeto UserInfo
                val email = userSnapshot.child("email").getValue(String::class.java) ?: ""
                val uid = userSnapshot.child("uid").getValue(String::class.java) ?: currentUser.uid
                val createdAt = userSnapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                
                val userInfo = User(
                    id = uid,
                    username = username,
                    email = email,
                    createdAt = createdAt
                )
                
                // Actualizar caché y estado
                cachedUserInfo = userInfo
                lastFetchTime = System.currentTimeMillis()
                _userProfileFlow.value = userInfo
                
                // Configurar listener para actualizaciones
                setupUserListener(username)
            }
        } catch (e: Exception) {
            Log.e("UserService", "Error al verificar registro: ${e.message}")
            
            // Si hay error pero tenemos caché, usarla como fallback
            if (cachedUserInfo != null) {
                _userProfileFlow.value = cachedUserInfo
            } else {
                _userProfileFlow.value = null
                throw e
            }
        }
    }

    private fun updateUserProfile(snapshot: DataSnapshot, username: String) {
        try {
            if (!snapshot.exists()) {
                Log.d("UserService", "Perfil no encontrado para $username")
                _userProfileFlow.value = null
                return
            }

            val userInfo = snapshot.getValue(User::class.java)?.copy(
                username = username,
                id = auth.currentUser?.uid ?: ""
            )

            if (userInfo != null) {
                Log.d("UserService", "Perfil actualizado: ${userInfo.username}")
                
                // Actualizar caché
                cachedUserInfo = userInfo
                lastFetchTime = System.currentTimeMillis()
                
                _userProfileFlow.value = userInfo
            } else {
                Log.e("UserService", "No se pudo cargar el perfil para $username")
                _userProfileFlow.value = null
            }
        } catch (e: Exception) {
            Log.e("UserService", "Error al actualizar perfil: ${e.message}")
            _userProfileFlow.value = null
        }
    }

    private fun setupUserListener(username: String) {
        // Remover listener anterior
        removeUserListener()

        Log.d("UserService", "Configurando listener para usuario: $username")
        
        // Intentar mantener esta referencia sincronizada para offline
        usersRef.child(username).keepSynced(true)

        userListener = usersRef.child(username).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                updateUserProfile(snapshot, username)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("UserService", "Error en listener de perfil: ${error.message}")
                _userProfileFlow.value = null
            }
        })
    }

    private fun removeUserListener() {
        userListener?.let { listener ->
            _userProfileFlow.value?.username?.let { username ->
                usersRef.child(username).removeEventListener(listener)
                Log.d("UserService", "Listener removido para $username")
            }
            userListener = null
        }
    }

    fun cleanup() {
        Log.d("UserService", "Limpiando recursos")
        removeUserListener()

        authStateListener?.let { listener ->
            auth.removeAuthStateListener(listener)
            authStateListener = null
        }

        _userProfileFlow.value = null
    }
    
    private fun validateUsername(username: String): String? {
        // Eliminar espacios al inicio y final
        val trimmedUsername = username.trim()

        return when {
            trimmedUsername.isBlank() -> "El nombre de usuario no puede estar vacío"
            trimmedUsername.length < 3 -> "El nombre de usuario debe tener al menos 3 caracteres"
            trimmedUsername.length > 30 -> "El nombre de usuario no puede tener más de 30 caracteres"
            trimmedUsername.contains(" ") -> "El nombre de usuario no puede contener espacios"
            !trimmedUsername.matches("^[a-zA-Z0-9]+$".toRegex()) ->
                "El nombre de usuario solo puede contener letras y números"
            else -> null
        }
    }

    suspend fun registerUsername(username: String) {
        try {
            val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")

            // Validar formato del username
            validateUsername(username)?.let { error ->
                throw Exception(error)
            }

            // Verificar si el usuario ya tiene un perfil
            val existingProfileQuery = usernamesRef
                .orderByValue()
                .equalTo(currentUser.uid)
                .get()
                .await()

            if (existingProfileQuery.exists()) {
                val existingUsername = existingProfileQuery.children.first().key
                throw Exception("Ya tienes un perfil registrado como: $existingUsername")
            }

            // Verificar si el username está disponible
            val usernameCheck = usernamesRef.child(username).get().await()
            if (usernameCheck.exists()) {
                throw Exception("El nombre de usuario ya está en uso")
            }

            // Crear el perfil
            val userInfo = User(
                id = currentUser.uid,
                username = username,
                email = currentUser.email ?: "",
                createdAt = System.currentTimeMillis()
            )

            // Actualizar la base de datos de forma atómica
            database.reference.updateChildren(
                mapOf(
                    "usernames/$username" to currentUser.uid,
                    "users/$username" to userInfo.toMap()
                )
            ).await()

            // Actualizar caché
            cachedUsername = username
            cachedUserInfo = userInfo
            lastFetchTime = System.currentTimeMillis()
            
            // Configurar listener y actualizar flujo
            setupUserListener(username)
            _userProfileFlow.value = userInfo
            
            Log.d("UserService", "Usuario registrado exitosamente: $username")

        } catch (e: Exception) {
            Log.e("UserService", "Error en registro: ${e.message}")
            throw e
        }
    }

    private fun User.toMap() = mapOf(
        "uid" to id,
        "email" to email,
        "createdAt" to createdAt
    )
}