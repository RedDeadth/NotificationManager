package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.dynamictecnologies.notificationmanager.data.repository.IAuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.Flow
import com.google.firebase.auth.FirebaseUser
import com.dynamictecnologies.notificationmanager.data.exceptions.toAuthException

class AuthViewModel(
    private val authRepository: IAuthRepository
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

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true)
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

    fun getGoogleSignInIntent(): Flow<Intent> {
        return authRepository.getGoogleSignInIntent()
    }

    fun handleGoogleSignInResult(result: Intent) {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true)
                val task = GoogleSignIn.getSignedInAccountFromIntent(result)
                val account = task.getResult(ApiException::class.java)
                val result = authRepository.handleGoogleSignIn(account.idToken!!)
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

    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true)
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

    fun signOut() {
        viewModelScope.launch {
            try {
                val result = authRepository.signOut()
                result.onSuccess {
                    _authState.value = AuthState(isSessionValid = authRepository.isSessionValid())
                }.onFailure { error ->
                    handleException(error)
                }
            } catch (e: Exception) {
                handleException(e)
            }
        }
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }

    private fun handleException(error: Throwable) {
        val authError = error.toAuthException()
        _authState.value = _authState.value.copy(
            error = when (authError.code) {
                AuthErrorCode.INVALID_CREDENTIALS -> "Credenciales inválidas"
                AuthErrorCode.USER_NOT_FOUND -> "Usuario no encontrado"
                AuthErrorCode.WEAK_PASSWORD -> "Contraseña muy débil"
                AuthErrorCode.EMAIL_ALREADY_IN_USE -> "Email ya en uso"
                AuthErrorCode.NETWORK_ERROR -> "Error de red"
                AuthErrorCode.SESSION_EXPIRED -> "Sesión expirada"
                else -> authError.message ?: "Error desconocido"
            }
        )
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