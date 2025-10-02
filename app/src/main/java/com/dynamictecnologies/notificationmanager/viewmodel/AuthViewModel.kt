package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.dynamictecnologies.notificationmanager.data.mapper.AuthErrorMapper
import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.dynamictecnologies.notificationmanager.domain.usecases.GetCurrentUserUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.RegisterWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithGoogleUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignOutUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.ValidateSessionUseCase
import com.dynamictecnologies.notificationmanager.ui.auth.GoogleSignInHelper
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val registerWithEmailUseCase: RegisterWithEmailUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val validateSessionUseCase: ValidateSessionUseCase,
    private val googleSignInHelper: GoogleSignInHelper,
    private val errorMapper: AuthErrorMapper
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkAuthState()
    }

    /**
     * Verifica el estado de autenticación actual
     */
    fun checkAuthState() {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true)
                val isSessionValid = validateSessionUseCase()

                getCurrentUserUseCase().collect { user ->
                    _authState.value = AuthState(
                        isAuthenticated = user != null,
                        currentUser = user,
                        isSessionValid = isSessionValid
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
            executeAuthOperation {
                signInWithEmailUseCase(email, password)
            }
        }
    }

    /**
     * Registra un nuevo usuario con email y contraseña
     */
    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            executeAuthOperation {
                registerWithEmailUseCase(email, password)
            }
        }
    }

    /**
     * Obtiene el intent de Google Sign In
     */
    fun getGoogleSignInIntent(): Intent {
        return googleSignInHelper.getSignInIntent()
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

                val idToken = googleSignInHelper.getIdTokenFromIntent(result)
                executeAuthOperation {
                    signInWithGoogleUseCase(idToken)
                }

            } catch (e: ApiException) {
                _authState.value = _authState.value.copy(
                    error = "Error de Google Sign In: ${e.localizedMessage}",
                    isLoading = false
                )
            } catch (e: IllegalStateException) {
                _authState.value = _authState.value.copy(
                    error = e.message ?: "Error desconocido",
                    isLoading = false
                )
            } catch (e: Exception) {
                handleException(e)
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

                val result = signOutUseCase()
                result.onSuccess {
                    googleSignInHelper.signOut()
                    val isSessionValid = validateSessionUseCase()
                    _authState.value = AuthState(isSessionValid = isSessionValid)
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
     * Función privada que encapsula la lógica común de operaciones de autenticación.
     * Aplica principio DRY evitando código duplicado.
     */
    private suspend fun executeAuthOperation(operation: suspend () -> Result<User>) {
        try {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            val result = operation()
            result.onSuccess { user ->
                val isSessionValid = validateSessionUseCase()
                _authState.value = AuthState(
                    isAuthenticated = true,
                    currentUser = user,
                    isSessionValid = isSessionValid
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

    /**
     * Maneja excepciones y las convierte en mensajes de error localizados.
     * Aplica principio DRY evitando código duplicado.
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

    /**
     * Estado de autenticación
     */
    data class AuthState(
        val isAuthenticated: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val currentUser: User? = null,
        val isSessionValid: Boolean = false
    )

    /**
     * Factory para crear instancias del ViewModel con inyección de dependencias
     */
    class Factory(
        private val signInWithEmailUseCase: SignInWithEmailUseCase,
        private val registerWithEmailUseCase: RegisterWithEmailUseCase,
        private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
        private val signOutUseCase: SignOutUseCase,
        private val getCurrentUserUseCase: GetCurrentUserUseCase,
        private val validateSessionUseCase: ValidateSessionUseCase,
        private val googleSignInHelper: GoogleSignInHelper,
        private val errorMapper: AuthErrorMapper
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(
                    signInWithEmailUseCase,
                    registerWithEmailUseCase,
                    signInWithGoogleUseCase,
                    signOutUseCase,
                    getCurrentUserUseCase,
                    validateSessionUseCase,
                    googleSignInHelper,
                    errorMapper
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
