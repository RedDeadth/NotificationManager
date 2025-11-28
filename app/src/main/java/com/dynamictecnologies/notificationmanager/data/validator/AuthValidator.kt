package com.dynamictecnologies.notificationmanager.data.validator

import android.util.Patterns

/**
 * Validador de credenciales de autenticación siguiendo el principio de responsabilidad única (SRP).
 * Esta clase solo se encarga de validar datos de entrada.
 */
class AuthValidator(
    private val passwordValidator: PasswordValidator = PasswordValidator()
) {
    
    /**
     * Resultado de la validación
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val error: ValidationError, val details: List<String> = emptyList()) : ValidationResult()
    }
    
    /**
     * Tipos de errores de validación
     */
    enum class ValidationError {
        EMPTY_EMAIL,
        INVALID_EMAIL_FORMAT,
        EMPTY_PASSWORD,
        WEAK_PASSWORD,
        PASSWORDS_DO_NOT_MATCH
    }
    
    /**
     * Valida el formato del email
     */
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult.Invalid(ValidationError.EMPTY_EMAIL)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> 
                ValidationResult.Invalid(ValidationError.INVALID_EMAIL_FORMAT)
            else -> ValidationResult.Valid
        }
    }
    
    /**
     * Valida la contraseña usando el validador robusto
     */
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
    
    /**
     * Valida que las contraseñas coincidan
     */
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
    

    
    /**
     * Obtiene el mensaje de error localizado
     */
    fun getErrorMessage(error: ValidationError, details: List<String> = emptyList()): String {
        return when (error) {
            ValidationError.EMPTY_EMAIL -> "El email es requerido"
            ValidationError.INVALID_EMAIL_FORMAT -> "Formato de email inválido"
            ValidationError.EMPTY_PASSWORD -> "La contraseña es requerida"
            ValidationError.WEAK_PASSWORD -> {
                if (details.isNotEmpty()) {
                    "Contraseña débil:\n${details.joinToString("\n• ", prefix = "• ")}"
                } else {
                    "La contraseña no cumple con los requisitos de seguridad"
                }
            }
            ValidationError.PASSWORDS_DO_NOT_MATCH -> "Las contraseñas no coinciden"
        }
    }
}
