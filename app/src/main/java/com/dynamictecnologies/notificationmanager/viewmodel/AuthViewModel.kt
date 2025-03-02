package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.activity.result.ActivityResult
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _authState = MutableStateFlow(AuthState())
    val authState = _authState.asStateFlow()

    init {
        checkAuthState()
    }

    fun checkAuthState() {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            try {
                val user = authRepository.getCurrentUser()
                _authState.value = AuthState(
                    isAuthenticated = user != null,
                    currentUser = user
                )
            } catch (e: Exception) {
                _authState.value = AuthState(error = e.message)
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            try {
                val user = authRepository.signInWithEmail(email, password)
                _authState.value = AuthState(
                    isAuthenticated = true,
                    currentUser = user
                )
            } catch (e: Exception) {
                _authState.value = AuthState(error = e.message)
            }
        }
    }
    // Método para obtener el Intent de inicio de sesión con Google
    fun getGoogleSignInIntent(): Intent {
        return authRepository.getGoogleSignInIntent()
    }

    // Método para manejar el resultado del inicio de sesión con Google
    fun handleGoogleSignInResult(result: ActivityResult) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                // El token ID no será nulo aquí porque lo solicitamos en las opciones
                val user = authRepository.handleGoogleSignIn(account.idToken!!)
                _authState.value = AuthState(
                    isAuthenticated = true,
                    currentUser = user
                )
            } catch (e: Exception) {
                _authState.value = AuthState(error = e.message)
            }
        }
    }

    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)
            try {
                val user = authRepository.registerWithEmail(email, password)
                _authState.value = AuthState(
                    isAuthenticated = true,
                    currentUser = user
                )
            } catch (e: Exception) {
                _authState.value = AuthState(error = e.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.value = AuthState()
        }
    }
}

data class AuthState(
    val isAuthenticated: Boolean = false,
    val currentUser: FirebaseUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)



class AuthViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}