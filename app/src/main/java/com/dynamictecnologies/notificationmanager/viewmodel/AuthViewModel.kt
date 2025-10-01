package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.dynamictecnologies.notificationmanager.data.mapper.AuthErrorMapper
import com.dynamictecnologies.notificationmanager.data.repository.IAuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de autenticación que maneja el estado de la UI y coordina las operaciones de autenticación.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja el estado de UI y coordina llamadas al repository
 * - DIP: Depende de la abstracción IAuthRepository
 */
class AuthViewModel(
    private val authRepository: IAuthRepository,
    private val errorMapper: AuthErrorMapper = AuthErrorMapper()
) : ViewModel() {
    private val _authState = MutableStateFlow(AuthState(isSessionValid = authRepository.isSessionValid()))
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        _authState.value = AuthState(isSessionValid = authRepository.isSessionValid())
        checkAuthState()
    }

    fun checkAuthState() {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true)
                authRepository.getCurrentUser().collect { user ->
                    _authState.value = AuthState(
                        isAuthenticated = user != null,
                        currentUser = user,
                        isSessionValid = authRepository.isSessionValid()
                    )
                }
            } catch (e: Exception) {
                handleException(e)
            } finally {
                _authState.value = _authState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Inicia sesión con email y contraseña
     */
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true, error = null)
                val result = authRepository.signInWithEmail(email, password)
                result.onSuccess { user ->
                    _authState.value = AuthState(
                        isAuthenticated = true,
                        currentUser = user,
                        isSessionValid = authRepository.isSessionValid()
                    )
                }.onFailure { error ->
                    handleException(error)
                }
            } catch (e: Exception) {
                handleException(e)
            } finally {
                _authState.value = _authState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Obtiene el intent de Google Sign In
     */
    fun getGoogleSignInIntent(): Flow<Intent> {
        return authRepository.getGoogleSignInIntent()
    }

    /**
     * Maneja el resultado del inicio de sesión con Google
     */
    fun handleGoogleSignInResult(result: Intent?) {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true, error = null)
                
                if (result == null) {
                    _authState.value = _authState.value.copy(
                        error = "Error al obtener resultado de Google Sign In",
                        isLoading = false
                    )
                    return@launch
                }
                
                val task = GoogleSignIn.getSignedInAccountFromIntent(result)
                val account = task.getResult(ApiException::class.java)
                
                if (account?.idToken == null) {
                    _authState.value = _authState.value.copy(
                        error = "No se pudo obtener el token de Google",
                        isLoading = false
                    )
                    return@launch
                }
                
                val authResult = authRepository.handleGoogleSignIn(account.idToken!!)
                authResult.onSuccess { user ->
                    _authState.value = AuthState(
                        isAuthenticated = true,
                        currentUser = user,
                        isSessionValid = authRepository.isSessionValid()
                    )
                }.onFailure { error ->
                    handleException(error)
                }
            } catch (e: ApiException) {
                _authState.value = _authState.value.copy(
                    error = "Error de Google Sign In: ${e.localizedMessage}",
                    isLoading = false
                )
            } catch (e: Exception) {
                handleException(e)
            } finally {
                _authState.value = _authState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Registra un nuevo usuario con email y contraseña
     */
    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true, error = null)
                val result = authRepository.registerWithEmail(email, password)
                result.onSuccess { user ->
                    _authState.value = AuthState(
                        isAuthenticated = true,
                        currentUser = user,
                        isSessionValid = authRepository.isSessionValid()
                    )
                }.onFailure { error ->
                    handleException(error)
                }
            } catch (e: Exception) {
                handleException(e)
            } finally {
                _authState.value = _authState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Cierra la sesión del usuario actual
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true, error = null)
                val result = authRepository.signOut()
                result.onSuccess {
                    _authState.value = AuthState(isSessionValid = authRepository.isSessionValid())
                }.onFailure { error ->
                    handleException(error)
                }
            } catch (e: Exception) {
                handleException(e)
            } finally {
                _authState.value = _authState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Limpia el error actual del estado
     */
    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }

    /**
     * Maneja excepciones y las convierte en mensajes de error localizados
     */
    private fun handleException(error: Throwable) {
        val errorMessage = if (error is AuthException) {
            errorMapper.getLocalizedErrorMessage(error)
        } else {
            val authException = errorMapper.mapException(error)
            errorMapper.getLocalizedErrorMessage(authException)
        }
        
        _authState.value = _authState.value.copy(error = errorMessage)
    }

    data class AuthState(
        val isAuthenticated: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val currentUser: FirebaseUser? = null,
        val isSessionValid: Boolean
    )

    class AuthViewModelFactory(
        private val authRepository: IAuthRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}