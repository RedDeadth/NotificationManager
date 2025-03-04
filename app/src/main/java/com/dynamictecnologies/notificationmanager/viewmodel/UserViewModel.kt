package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.service.UserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserViewModel(
    private val userService: UserService
) : ViewModel() {
    private val _usernameState = MutableStateFlow<UsernameState>(UsernameState.Initial)
    val usernameState = _usernameState.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<String>>(emptyList())
    val availableUsers = _availableUsers.asStateFlow()

    // Añadir esta declaración que faltaba
    private val _sharedUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val sharedUsers = _sharedUsers.asStateFlow()

    init {
        checkUserRegistration()
    }

    private fun checkUserRegistration() {
        viewModelScope.launch {
            userService.checkCurrentUserRegistration()
                .onSuccess { userInfo ->
                    if (userInfo == null) {
                        _usernameState.value = UsernameState.Initial
                    } else {
                        _usernameState.value = UsernameState.Success(userInfo)
                        loadSharedUsers() // Cargar usuarios compartidos al iniciar
                    }
                }
                .onFailure { error ->
                    Log.e("UserViewModel", "Error al verificar registro: ${error.message}")
                    _usernameState.value = UsernameState.Error(error.message ?: "Error desconocido")
                }
        }
    }

    fun registerUsername(username: String) {
        viewModelScope.launch {
            Log.d("UserViewModel", "Intentando registrar username: $username")
            _usernameState.value = UsernameState.Loading

            userService.registerUsername(username)
                .onSuccess { userInfo ->
                    Log.d("UserViewModel", "Username registrado exitosamente: $userInfo")
                    _usernameState.value = UsernameState.Success(userInfo)
                }
                .onFailure { error ->
                    Log.e("UserViewModel", "Error al registrar username: ${error.message}")
                    _usernameState.value = UsernameState.Error(error.message ?: "Error desconocido")
                }
        }
    }

    fun loadAvailableUsers() {
        viewModelScope.launch {
            userService.getAvailableUsers()
                .onSuccess { users ->
                    _availableUsers.value = users
                    Log.d("UserViewModel", "Usuarios disponibles cargados: ${users.size}")
                }
                .onFailure { error ->
                    Log.e("UserViewModel", "Error al cargar usuarios disponibles: ${error.message}")
                }
        }
    }

    fun shareWithUser(username: String) {
        viewModelScope.launch {
            Log.d("UserViewModel", "Intentando compartir con: $username")
            userService.shareNotificationsAccess(username)
                .onSuccess {
                    Log.d("UserViewModel", "Compartido exitosamente con: $username")
                    loadSharedUsers() // Recargar la lista después de compartir
                }
                .onFailure { error ->
                    Log.e("UserViewModel", "Error al compartir: ${error.message}")
                }
        }
    }

    fun loadSharedUsers() {
        viewModelScope.launch {
            try {
                Log.d("UserViewModel", "Cargando usuarios compartidos")
                userService.getSharedUsers()
                    .onSuccess { users ->
                        Log.d("UserViewModel", "Usuarios compartidos cargados: ${users.size}")
                        _sharedUsers.value = users
                    }
                    .onFailure { error ->
                        Log.e("UserViewModel", "Error al cargar usuarios compartidos: ${error.message}")
                    }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error al cargar usuarios compartidos", e)
            }
        }
    }
}

sealed class UsernameState {
    object Initial : UsernameState()
    object Loading : UsernameState()
    data class Success(val userInfo: UserInfo) : UsernameState()
    data class Error(val message: String) : UsernameState()
}

class UserViewModelFactory(
    private val userService: UserService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserViewModel(userService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}