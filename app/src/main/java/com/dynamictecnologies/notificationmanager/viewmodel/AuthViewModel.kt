package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import kotlinx.coroutines.flow.Flow
import com.dynamictecnologies.notificationmanager.data.repository.IAuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: IAuthRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch(dispatcher) {
            authRepository.currentUser.collect { user ->
                _authState.value = if (user != null) {
                    AuthState.Authenticated(user)
                } else {
                    AuthState.Unauthenticated
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch(dispatcher) {
            _authState.value = AuthState.Loading
            when (val result = authRepository.signInWithEmail(email, password)) {
                is Result.Success -> {
                    result.getOrNull()?.let { user ->
                        _authState.value = AuthState.Authenticated(user)
                    } ?: run {
                        _authState.value = AuthState.Error("Error desconocido al iniciar sesión")
                    }
                }
                is Result.Failure -> {
                    _authState.value = AuthState.Error(
                        result.exceptionOrNull()?.message ?: "Error al iniciar sesión"
                    )
                }
            }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch(dispatcher) {
            _authState.value = AuthState.Loading
            when (val result = authRepository.registerWithEmail(email, password)) {
                is Result.Success -> {
                    result.getOrNull()?.let { user ->
                        _authState.value = AuthState.Authenticated(user)
                    } ?: run {
                        _authState.value = AuthState.Error("Error desconocido al registrar")
                    }
                }
                is Result.Failure -> {
                    _authState.value = AuthState.Error(
                        result.exceptionOrNull()?.message ?: "Error al registrar"
                    )
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch(dispatcher) {
            _authState.value = AuthState.Loading
            when (val result = authRepository.handleGoogleSignIn(idToken)) {
                is Result.Success -> {
                    result.getOrNull()?.let { user ->
                        _authState.value = AuthState.Authenticated(user)
                    } ?: run {
                        _authState.value = AuthState.Error("Error desconocido con Google")
                    }
                }
                is Result.Failure -> {
                    _authState.value = AuthState.Error(
                        result.exceptionOrNull()?.message ?: "Error al iniciar con Google"
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(dispatcher) {
            when (val result = authRepository.signOut()) {
                is Result.Success -> {
                    _authState.value = AuthState.Unauthenticated
                }
                is Result.Failure -> {
                    _authState.value = AuthState.Error(
                        result.exceptionOrNull()?.message ?: "Error al cerrar sesión"
                    )
                }
            }
        }
    }

    fun clearError() {
        _authState.value = when (val current = _authState.value) {
            is AuthState.Error -> AuthState.Unauthenticated
            else -> current
        }
    }

    fun getGoogleSignInIntent(): Flow<Intent> = authRepository.getGoogleSignInIntent()

    // Estados de autenticación
    sealed class AuthState {
        object Initial : AuthState()
        object Loading : AuthState()
        object Unauthenticated : AuthState()
        data class Authenticated(val user: FirebaseUser) : AuthState()
        data class Error(val message: String) : AuthState()
    }
}