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
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.GetCurrentUserUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.RegisterUserWithUsernameUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithGoogleUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignOutUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.ValidateSessionUseCase
import com.dynamictecnologies.notificationmanager.presentation.auth.GoogleSignInHelper
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
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

    private val _loginFormState = MutableStateFlow(LoginFormState())
    val loginFormState: StateFlow<LoginFormState> = _loginFormState.asStateFlow()

    private var registerEmailValidationJob: Job? = null
    private var registerPasswordValidationJob: Job? = null
    private var registerConfirmPasswordValidationJob: Job? = null
    private var registerUsernameValidationJob: Job? = null
    private var loginEmailValidationJob: Job? = null
    private var loginPasswordValidationJob: Job? = null

    init {
        // Verificar estado de Firebase Auth INMEDIATAMENTE (síncrono)
        viewModelScope.launch {
            try {
                // 1. Verificar síncronamente si hay usuario de Firebase
                val firebaseUser = authRepository.awaitFirebaseUser()
                
                if (firebaseUser != null) {
                    // Hay sesión activa, actualizar estado inmediatamente
                    _authState.value = _authState.value.copy(
                        isInitializing = false,
                        isAuthenticated = true,
                        currentUser = User(
                            id = firebaseUser.uid,
                            email = firebaseUser.email ?: "",
                            username = firebaseUser.displayName ?: "",
                            displayName = firebaseUser.displayName,
                            isEmailVerified = firebaseUser.isEmailVerified
                        )
                    )
                } else {
                    // No hay sesión
                    _authState.value = _authState.value.copy(
                        isInitializing = false,
                        isAuthenticated = false
                    )
                }
                
                // 2. Luego escuchar cambios con Flow
                checkAuthState()
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isInitializing = false,
                    isAuthenticated = false
                )
            }
        }
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
                        isSessionValid = isSessionValid,
                        isInitializing = false  // Ya terminó de inicializar
                    )
                }
            } catch (e: Exception) {
                handleException(e)
                _authState.value = _authState.value.copy(isInitializing = false)
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

    fun registerWithEmail(email: String, password: String, confirmPassword: String, username: String) {
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(isLoading = true, error = null)
                
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
                        isSessionValid = isSessionValid,
                        isInitializing = false
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
                    _authState.value = AuthState(
                        isSessionValid = isSessionValid,
                        isInitializing = false
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
                    isSessionValid = isSessionValid,
                    isInitializing = false
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
        val isInitializing: Boolean = true,  // True mientras verificamos sesión de Firebase
        val error: String? = null,
        val currentUser: User? = null,
        val isSessionValid: Boolean = false
    )

    data class LoginFormState(
        val email: String = "",
        val password: String = "",
        val emailError: String? = null,
        val passwordError: String? = null,
        val isFormValid: Boolean = false
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
        _registerFormState.value = _registerFormState.value.copy(email = email)
        
        registerEmailValidationJob?.cancel()
        registerEmailValidationJob = viewModelScope.launch {
            delay(300)
            
            val error = when(val result = authValidator.validateEmail(email)) {
                is com.dynamictecnologies.notificationmanager.data.validator.AuthValidator.ValidationResult.Invalid -> 
                    authValidator.getErrorMessage(result.error, result.details)
                else -> null
            }
            _registerFormState.value = _registerFormState.value.copy(emailError = error)
            updateFormValidity()
        }
    }

    fun updateRegisterPassword(password: String) {
        _registerFormState.value = _registerFormState.value.copy(password = password)
        
        registerPasswordValidationJob?.cancel()
        registerPasswordValidationJob = viewModelScope.launch {
            delay(300)
            
            val error = when(val result = authValidator.validatePassword(password)) {
                is com.dynamictecnologies.notificationmanager.data.validator.AuthValidator.ValidationResult.Invalid -> 
                    authValidator.getErrorMessage(result.error, result.details)
                else -> null
            }
            _registerFormState.value = _registerFormState.value.copy(passwordError = error)
            updateFormValidity()
        }
    }

    fun updateRegisterConfirmPassword(confirmPassword: String) {
        _registerFormState.value = _registerFormState.value.copy(confirmPassword = confirmPassword)
        
        registerConfirmPasswordValidationJob?.cancel()
        registerConfirmPasswordValidationJob = viewModelScope.launch {
            delay(300)
            
            val state = _registerFormState.value
            val error = when(val result = authValidator.validatePasswordMatch(state.password, confirmPassword)) {
                is com.dynamictecnologies.notificationmanager.data.validator.AuthValidator.ValidationResult.Invalid -> 
                    authValidator.getErrorMessage(result.error, result.details)
                else -> null
            }
            _registerFormState.value = state.copy(confirmPasswordError = error)
            updateFormValidity()
        }
    }

    fun updateRegisterUsername(username: String) {
        _registerFormState.value = _registerFormState.value.copy(username = username)
        
        registerUsernameValidationJob?.cancel()
        registerUsernameValidationJob = viewModelScope.launch {
            delay(300)
            
            val error = when(val result = usernameValidator.validate(username)) {
                is com.dynamictecnologies.notificationmanager.data.validator.UsernameValidator.ValidationResult.Invalid -> 
                    usernameValidator.getErrorMessage(result.error)
                else -> null
            }
            _registerFormState.value = _registerFormState.value.copy(usernameError = error)
            updateFormValidity()
        }
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

    fun updateLoginEmail(email: String) {
        _loginFormState.value = _loginFormState.value.copy(email = email)
        
        loginEmailValidationJob?.cancel()
        loginEmailValidationJob = viewModelScope.launch {
            delay(300)
            
            val error = when(val result = authValidator.validateEmail(email)) {
                is com.dynamictecnologies.notificationmanager.data.validator.AuthValidator.ValidationResult.Invalid -> 
                    authValidator.getErrorMessage(result.error, result.details)
                else -> null
            }
            _loginFormState.value = _loginFormState.value.copy(emailError = error)
            updateLoginFormValidity()
        }
    }

    fun updateLoginPassword(password: String) {
        _loginFormState.value = _loginFormState.value.copy(password = password)
        
        loginPasswordValidationJob?.cancel()
        loginPasswordValidationJob = viewModelScope.launch {
            delay(300)
            
            val error = if (password.isBlank()) 
                AuthStrings.ValidationErrors.EMPTY_PASSWORD 
            else null
            
            _loginFormState.value = _loginFormState.value.copy(passwordError = error)
            updateLoginFormValidity()
        }
    }

    private fun updateLoginFormValidity() {
        val state = _loginFormState.value
        val isValid = state.email.isNotBlank() &&
                      state.password.isNotBlank() &&
                      state.emailError == null &&
                      state.passwordError == null
        _loginFormState.value = state.copy(isFormValid = isValid)
    }

    fun clearLoginForm() {
        _loginFormState.value = LoginFormState()
    }

    class Factory(
        private val authRepository: AuthRepository,
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
                    authRepository,
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
