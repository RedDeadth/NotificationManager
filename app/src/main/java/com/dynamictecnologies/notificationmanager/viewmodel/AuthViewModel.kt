package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.constants.AuthStrings
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.dynamictecnologies.notificationmanager.data.mapper.AuthErrorMapper
import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.dynamictecnologies.notificationmanager.domain.usecases.GetCurrentUserUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.RegisterUserWithUsernameUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithGoogleUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignOutUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.ValidateSessionUseCase
import com.dynamictecnologies.notificationmanager.presentation.auth.GoogleSignInHelper
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val registerUserWithUsernameUseCase: RegisterUserWithUsernameUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val validateSessionUseCase: ValidateSessionUseCase,
    private val googleSignInHelper: GoogleSignInHelper,
    private val errorMapper: AuthErrorMapper,
    private val authValidator: com.dynamictecnologies.notificationmanager.data.validator.AuthValidator,
    private val usernameValidator: com.dynamictecnologies.notificationmanager.data.validator.UsernameValidator
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _registerFormState = MutableStateFlow(RegisterFormState())
    val registerFormState: StateFlow<RegisterFormState> = _registerFormState.asStateFlow()

    init {
        checkAuthState()
    }

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

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            executeAuthOperation {
                signInWithEmailUseCase(email, password)
            }
        }
    }

    /**
     * Registra un nuevo usuario con email, contraseña y username.
     * Delega toda la orquestación al UseCase.
     */
    fun registerWithEmail(email: String, password: String, confirmPassword: String, username: String) {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true, error = null)
                
                // Validación simple de confirmación en UI (resto de validación en domain)
                if (password != confirmPassword) {
                    _authState.value = _authState.value.copy(
                        error = AuthStrings.ValidationErrors.PASSWORDS_DO_NOT_MATCH,
                        isLoading = false
                    )
                    return@launch
                }
                
                // Delegar todo al UseCase de orquestación
                val result = registerUserWithUsernameUseCase(email, password, username)
                
                result.onSuccess { profile ->
                    val isSessionValid = validateSessionUseCase()
                    _authState.value = AuthState(
                        isAuthenticated = true,
                        currentUser = User(
                            id = profile.uid,
                            username = profile.username,
                            email = profile.email
                        ),
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
    }

    fun getGoogleSignInIntent(): Intent {
        return googleSignInHelper.getSignInIntent()
    }

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

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }

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
        val currentUser: User? = null,
        val isSessionValid: Boolean = false
    )

    data class RegisterFormState(
        val email: String = "",
        val password: String = "",
        val confirmPassword: String = "",
        val username: String = "",
        val emailError: String? = null,
        val passwordError: String? = null,
        val confirmPasswordError: String? = null,
        val usernameError: String? = null,
        val isFormValid: Boolean = false
    )

    fun updateRegisterEmail(email: String) {
        val error = when(val result = authValidator.validateEmail(email)) {
            is com.dynamictecnologies.notificationmanager.data.validator.AuthValidator.ValidationResult.Invalid -> 
                authValidator.getErrorMessage(result.error, result.details)
            else -> null
        }
        _registerFormState.value = _registerFormState.value.copy(
            email = email,
            emailError = error
        )
        updateFormValidity()
    }

    fun updateRegisterPassword(password: String) {
        val error = when(val result = authValidator.validatePassword(password)) {
            is com.dynamictecnologies.notificationmanager.data.validator.AuthValidator.ValidationResult.Invalid -> 
                authValidator.getErrorMessage(result.error, result.details)
            else -> null
        }
        _registerFormState.value = _registerFormState.value.copy(
            password = password,
            passwordError = error
        )
        updateFormValidity()
    }

    fun updateRegisterConfirmPassword(confirmPassword: String) {
        val state = _registerFormState.value
        val error = when(val result = authValidator.validatePasswordMatch(state.password, confirmPassword)) {
            is com.dynamictecnologies.notificationmanager.data.validator.AuthValidator.ValidationResult.Invalid -> 
                authValidator.getErrorMessage(result.error, result.details)
            else -> null
        }
        _registerFormState.value = state.copy(
            confirmPassword = confirmPassword,
            confirmPasswordError = error
        )
        updateFormValidity()
    }

    fun updateRegisterUsername(username: String) {
        val error = when(val result = usernameValidator.validate(username)) {
            is com.dynamictecnologies.notificationmanager.data.validator.UsernameValidator.ValidationResult.Invalid -> 
                usernameValidator.getErrorMessage(result.error)
            else -> null
        }
        _registerFormState.value = _registerFormState.value.copy(
            username = username,
            usernameError = error
        )
        updateFormValidity()
    }

    private fun updateFormValidity() {
        val state = _registerFormState.value
        val isValid = state.email.isNotBlank() &&
                      state.password.isNotBlank() &&
                      state.confirmPassword.isNotBlank() &&
                      state.username.isNotBlank() &&
                      state.emailError == null &&
                      state.passwordError == null &&
                      state.confirmPasswordError == null &&
                      state.usernameError == null
        
        _registerFormState.value = state.copy(isFormValid = isValid)
    }

    fun clearRegisterForm() {
        _registerFormState.value = RegisterFormState()
    }

    class Factory(
        private val signInWithEmailUseCase: SignInWithEmailUseCase,
        private val registerUserWithUsernameUseCase: RegisterUserWithUsernameUseCase,
        private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
        private val signOutUseCase: SignOutUseCase,
        private val getCurrentUserUseCase: GetCurrentUserUseCase,
        private val validateSessionUseCase: ValidateSessionUseCase,
        private val googleSignInHelper: GoogleSignInHelper,
        private val errorMapper: AuthErrorMapper,
        private val authValidator: com.dynamictecnologies.notificationmanager.data.validator.AuthValidator,
        private val usernameValidator: com.dynamictecnologies.notificationmanager.data.validator.UsernameValidator
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(
                    signInWithEmailUseCase,
                    registerUserWithUsernameUseCase,
                    signInWithGoogleUseCase,
                    signOutUseCase,
                    getCurrentUserUseCase,
                    validateSessionUseCase,
                    googleSignInHelper,
                    errorMapper,
                    authValidator,
                    usernameValidator
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
