package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.dynamictecnologies.notificationmanager.domain.usecases.user.GetUserProfileUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.user.RefreshUserProfileUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.user.RegisterUsernameUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gestión de perfiles de usuario.
 * Refactorizado para seguir Clean Architecture y SOLID.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja UI state para perfiles
 * - DIP: Depende de abstracciones (UseCases) no de Firebase directamente
 * - Clean Architecture: No tiene lógica de negocio, solo coordina UseCases
 * - DRY: Eliminada lógica duplicada de validación y caché
 */
class UserViewModel(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val registerUsernameUseCase: RegisterUsernameUseCase,
    private val refreshUserProfileUseCase: RefreshUserProfileUseCase
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState = _errorState.asStateFlow()

    init {
        observeUserProfile()
    }

    /**
     * Observa cambios en el perfil del usuario
     */
    private fun observeUserProfile() {
        viewModelScope.launch {
            try {
                getUserProfileUseCase()
                    .collect { profile ->
                        _userProfile.value = profile
                        Log.d("UserViewModel", "Perfil actualizado: ${profile?.username ?: "null"}")
                    }
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error observando perfil: ${e.message}")
                _errorState.value = e.message
            }
        }
    }

    /**
     * Refresca el perfil desde el servidor
     */
    fun refreshProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorState.value = null
                
                Log.d("UserViewModel", "Refrescando perfil...")
                
                val result = refreshUserProfileUseCase()
                
                result.onSuccess { profile ->
                    Log.d("UserViewModel", "Perfil refrescado: ${profile.username}")
                    // El perfil se actualizará automáticamente vía Flow
                }.onFailure { error ->
                    Log.e("UserViewModel", "Error refrescando perfil: ${error.message}")
                    // No mostrar error si simplemente no hay perfil aún
                    if (!error.message.orEmpty().contains("Perfil no encontrado")) {
                        _errorState.value = error.message
                    }
                }
                
            } catch (e: Exception) {
                Log.e("UserViewModel", "Excepción refrescando perfil: ${e.message}")
                _errorState.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Registra un username para el usuario actual
     */
    fun registerUsername(username: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorState.value = null

                Log.d("UserViewModel", "Registrando username: $username")
                
                // Delegar toda la lógica al UseCase
                val result = registerUsernameUseCase(username)
                
                result.onSuccess { profile ->
                    Log.d("UserViewModel", "Username registrado exitosamente: ${profile.username}")
                    // El perfil se actualizará automáticamente vía Flow
                }.onFailure { error ->
                    Log.e("UserViewModel", "Error registrando username: ${error.message}")
                    _errorState.value = error.message
                }

            } catch (e: Exception) {
                Log.e("UserViewModel", "Excepción registrando username: ${e.message}")
                _errorState.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorState.value = null
    }

    /**
     * Limpia todos los datos del ViewModel cuando el usuario cierra sesión
     */
    fun clearData() {
        _userProfile.value = null
        _errorState.value = null
        Log.d("UserViewModel", "Datos de perfil limpiados")
    }
}

/**
 * Factory para crear instancias de UserViewModel con inyección de dependencias.
 * Refactorizado para usar UseCases en vez de Firebase directamente.
 */
class UserViewModelFactory(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val registerUsernameUseCase: RegisterUsernameUseCase,
    private val refreshUserProfileUseCase: RefreshUserProfileUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            return UserViewModel(
                getUserProfileUseCase = getUserProfileUseCase,
                registerUsernameUseCase = registerUsernameUseCase,
                refreshUserProfileUseCase = refreshUserProfileUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}