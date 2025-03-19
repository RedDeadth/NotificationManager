package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.service.UserService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.database.DataSnapshot

class UserViewModel(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _userProfile = MutableStateFlow<UserInfo?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState = _errorState.asStateFlow()

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorState.value = null
                
                val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")
                Log.d("UserViewModel", "Refrescando perfil para: ${currentUser.uid}")
                
                // Buscar primero el username basado en el UID
                val usernamesRef = database.reference.child("usernames")
                val usernameSnapshot = usernamesRef.orderByValue().equalTo(currentUser.uid).get().await()
                
                if (!usernameSnapshot.exists()) {
                    Log.d("UserViewModel", "No se encontró username para el UID: ${currentUser.uid}")
                    _errorState.value = null // No mostrar error, simplemente no hay perfil aún
                    _isLoading.value = false
                    return@launch
                }
                
                // Obtener el username
                val username = usernameSnapshot.children.firstOrNull()?.key
                if (username == null) {
                    Log.e("UserViewModel", "Username es nulo para el UID: ${currentUser.uid}")
                    _errorState.value = "Error al recuperar información de usuario"
                    _isLoading.value = false
                    return@launch
                }
                
                Log.d("UserViewModel", "Username encontrado: $username")
                
                // Obtener los datos del perfil basados en el username
                val userSnapshot = database.reference.child("users").child(username).get().await()
                
                if (!userSnapshot.exists()) {
                    Log.e("UserViewModel", "Perfil no encontrado para el username: $username")
                    _errorState.value = "Datos de perfil no encontrados"
                    _isLoading.value = false
                    return@launch
                }
                
                // Crear el objeto UserInfo manualmente
                val email = userSnapshot.child("email").getValue(String::class.java)
                val uid = userSnapshot.child("uid").getValue(String::class.java) ?: currentUser.uid
                val createdAt = userSnapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                
                val userInfo = UserInfo(
                    uid = uid,
                    username = username,
                    email = email,
                    createdAt = createdAt
                )
                
                Log.d("UserViewModel", "Perfil cargado: username=$username, email=$email")
                
                // Actualizar el estado
                _userProfile.value = userInfo
                
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error refrescando perfil: ${e.message}", e)
                _errorState.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerUsername(username: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorState.value = null

                val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")
                Log.d("UserViewModel", "Intentando registrar username: $username para UID: ${currentUser.uid}")

                // Validar formato del username
                validateUsername(username)?.let { error ->
                    throw Exception(error)
                }

                // Verificar si el usuario ya tiene un perfil
                val usernamesRef = database.reference.child("usernames")
                val existingUsernameSnapshot = usernamesRef.orderByValue().equalTo(currentUser.uid).get().await()

                if (existingUsernameSnapshot.exists()) {
                    val existingUsername = existingUsernameSnapshot.children.first().key
                    Log.e("UserViewModel", "Ya existe un perfil con username: $existingUsername")
                    throw Exception("Ya tienes un perfil registrado como: $existingUsername")
                }

                // Verificar si el username está disponible
                val usernameCheck = usernamesRef.child(username).get().await()
                if (usernameCheck.exists()) {
                    Log.e("UserViewModel", "Username ya en uso: $username")
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
                val updates = hashMapOf<String, Any?>(
                    "usernames/$username" to currentUser.uid,
                    "users/$username/uid" to currentUser.uid,
                    "users/$username/email" to (currentUser.email ?: ""),
                    "users/$username/createdAt" to System.currentTimeMillis()
                )

                // Aplicar las actualizaciones
                database.reference.updateChildren(updates).await()

                Log.d("UserViewModel", "Usuario registrado exitosamente: $username")
                
                // Actualizar el estado con el nuevo perfil
                _userProfile.value = userInfo
                
                // Refrescar el perfil para verificar que se guardó correctamente
                refreshProfile()

            } catch (e: Exception) {
                Log.e("UserViewModel", "Error en registro: ${e.message}")
                _errorState.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Valida el formato del nombre de usuario
     * @return mensaje de error o null si es válido
     */
    private fun validateUsername(username: String): String? {
        val trimmed = username.trim()
        return when {
            trimmed.isBlank() -> "El nombre de usuario no puede estar vacío"
            trimmed.length < 3 -> "El nombre de usuario debe tener al menos 3 caracteres"
            trimmed.length > 30 -> "El nombre de usuario no puede tener más de 30 caracteres"
            trimmed.contains(" ") -> "El nombre de usuario no puede contener espacios"
            !trimmed.matches("^[a-zA-Z0-9]+$".toRegex()) -> "El nombre de usuario solo puede contener letras y números"
            else -> null
        }
    }

    fun clearError() {
        _errorState.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // No hacemos nada ya que ya no dependemos de UserService
    }
}

class UserViewModelFactory(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            return UserViewModel(auth, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}