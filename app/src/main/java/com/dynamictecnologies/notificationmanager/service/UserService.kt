package com.dynamictecnologies.notificationmanager.service

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
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

class UserService(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val scope: CoroutineScope
) {
    private val usersRef = database.getReference("users")
    private val usernamesRef = database.getReference("usernames")

    private val _userProfileFlow = MutableStateFlow<UserInfo?>(null)
    val userProfileFlow = _userProfileFlow.asStateFlow()

    private var userListener: ValueEventListener? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    init {
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

            Log.d("UserService", "Verificando registro para UID: ${currentUser.uid}")

            val snapshot = usernamesRef.orderByValue()
                .equalTo(currentUser.uid)
                .get()
                .await()

            if (!snapshot.exists()) {
                Log.d("UserService", "No se encontró perfil para ${currentUser.uid}")
                _userProfileFlow.value = null
                return
            }

            val username = snapshot.children.firstOrNull()?.key
            if (username != null) {
                Log.d("UserService", "Perfil encontrado: $username")
                setupUserListener(username)
            } else {
                Log.e("UserService", "Username no encontrado para ${currentUser.uid}")
                _userProfileFlow.value = null
            }
        } catch (e: Exception) {
            Log.e("UserService", "Error al verificar registro: ${e.message}")
            _userProfileFlow.value = null
            throw e
        }
    }

    private fun updateUserProfile(snapshot: DataSnapshot, username: String) {
        try {
            if (!snapshot.exists()) {
                Log.d("UserService", "Perfil no encontrado para $username")
                _userProfileFlow.value = null
                return
            }

            val userInfo = snapshot.getValue(UserInfo::class.java)?.copy(
                username = username,
                uid = auth.currentUser?.uid ?: ""
            )

            if (userInfo != null) {
                Log.d("UserService", "Perfil actualizado: ${userInfo.username}")
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

    private fun createUserListener(username: String): ValueEventListener {
        return object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                updateUserProfile(snapshot, username)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("UserService", "Error al escuchar cambios: ${error.message}")
                _userProfileFlow.value = null
            }
        }
    }

    suspend fun registerUsername(username: String) {
        try {
            val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")

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
            val userInfo = UserInfo(
                uid = currentUser.uid,
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

            setupUserListener(username)
        } catch (e: Exception) {
            Log.e("UserService", "Error en registro: ${e.message}")
            throw e
        }
    }

    private fun UserInfo.toMap() = mapOf(
        "uid" to uid,
        "email" to email,
        "createdAt" to createdAt
    )
}