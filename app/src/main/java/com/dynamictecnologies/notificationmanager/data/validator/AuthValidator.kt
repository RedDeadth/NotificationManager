package com.dynamictecnologies.notificationmanager.data.validator

import android.util.Patterns
import com.dynamictecnologies.notificationmanager.data.constants.AuthStrings

/**
 * Validador de credenciales de autenticación siguiendo el principio de responsabilidad única (SRP).
 * Esta clase solo se encarga de validar datos de entrada.
 */
class AuthValidator(
    private val passwordValidator: PasswordValidator = PasswordValidator(),
    private val usernameValidator: UsernameValidator = UsernameValidator()
) {
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val error: ValidationError, val details: List<String> = emptyList()) : ValidationResult()
    }
    
    enum class ValidationError {
        EMPTY_EMAIL,
        INVALID_EMAIL_FORMAT,
        EMPTY_PASSWORD,
        WEAK_PASSWORD,
        PASSWORDS_DO_NOT_MATCH,
        INVALID_USERNAME
    }
    
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult.Invalid(ValidationError.EMPTY_EMAIL)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> 
                ValidationResult.Invalid(ValidationError.INVALID_EMAIL_FORMAT)
            else -> ValidationResult.Valid
        }
    }
    
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Invalid(ValidationError.EMPTY_PASSWORD)
            else -> {
                when (val result = passwordValidator.validate(password)) {
                    is PasswordValidator.ValidationResult.Valid -> ValidationResult.Valid
                    is PasswordValidator.ValidationResult.Invalid -> 
                        ValidationResult.Invalid(ValidationError.WEAK_PASSWORD, result.errors)
                }
            }
        }
    }
    
    fun validatePasswordMatch(password: String, confirmPassword: String): ValidationResult {
        return if (password != confirmPassword) {
            ValidationResult.Invalid(ValidationError.PASSWORDS_DO_NOT_MATCH)
        } else {
            ValidationResult.Valid
        }
    }
    
    /**
     * Valida credenciales de login (email y contraseña)
     * NOTA: Para login, solo verificamos que la contraseña no esté vacía,
     * no aplicamos reglas de complejidad (ya que es una contraseña existente)
     */
    fun validateLoginCredentials(email: String, password: String): ValidationResult {
        validateEmail(email).let { result ->
            if (result is ValidationResult.Invalid) return result
        }
        
        // Para login, solo verificamos que no esté vacía
        if (password.isBlank()) {
            return ValidationResult.Invalid(ValidationError.EMPTY_PASSWORD)
        }
        
        return ValidationResult.Valid
    }
    
    fun validateRegistrationCredentials(
        email: String,
        password: String,
        confirmPassword: String,
        username: String
    ): ValidationResult {
        // Validar email
        validateEmail(email).let { result ->
            if (result is ValidationResult.Invalid) return result
        }
        
        // Validar password
        validatePassword(password).let { result ->
            if (result is ValidationResult.Invalid) return result
        }
        
        // Validar confirmación de password
        validatePasswordMatch(password, confirmPassword).let { result ->
            if (result is ValidationResult.Invalid) return result
        }
        
        // Validar username
        val usernameValidation = usernameValidator.validate(username)
        if (usernameValidation is UsernameValidator.ValidationResult.Invalid) {
            return ValidationResult.Invalid(
                ValidationError.INVALID_USERNAME,
                listOf(usernameValidator.getErrorMessage(usernameValidation.error))
            )
        }
        
        return ValidationResult.Valid
    }
    

    
    fun getErrorMessage(error: ValidationError, details: List<String> = emptyList()): String {
        return when (error) {
            ValidationError.EMPTY_EMAIL -> AuthStrings.ValidationErrors.EMPTY_EMAIL
            ValidationError.INVALID_EMAIL_FORMAT -> AuthStrings.ValidationErrors.INVALID_EMAIL_FORMAT
            ValidationError.EMPTY_PASSWORD -> AuthStrings.ValidationErrors.EMPTY_PASSWORD
            ValidationError.WEAK_PASSWORD -> {
                if (details.isNotEmpty()) {
                    AuthStrings.ValidationErrors.WEAK_PASSWORD_WITH_DETAILS + 
                        details.joinToString("\n• ", prefix = "• ")
                } else {
                    AuthStrings.ValidationErrors.WEAK_PASSWORD
                }
            }
            ValidationError.PASSWORDS_DO_NOT_MATCH -> AuthStrings.ValidationErrors.PASSWORDS_DO_NOT_MATCH
            ValidationError.INVALID_USERNAME -> {
                if (details.isNotEmpty()) {
                    details.firstOrNull() ?: AuthStrings.ValidationErrors.EMPTY_USERNAME
                } else {
                    AuthStrings.ValidationErrors.EMPTY_USERNAME
                }
            }
        }
    }
}
