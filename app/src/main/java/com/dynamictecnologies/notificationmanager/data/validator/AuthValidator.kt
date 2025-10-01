package com.dynamictecnologies.notificationmanager.data.validator

import android.util.Patterns

/**
 * Validador de credenciales de autenticación siguiendo el principio de responsabilidad única (SRP).
 * Esta clase solo se encarga de validar datos de entrada.
 */
class AuthValidator {
    
    /**
     * Resultado de la validación
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val error: ValidationError) : ValidationResult()
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
     * Valida la contraseña
     */
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Invalid(ValidationError.EMPTY_PASSWORD)
            password.length < 6 -> ValidationResult.Invalid(ValidationError.WEAK_PASSWORD)
            else -> ValidationResult.Valid
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
     */
    fun validateLoginCredentials(email: String, password: String): ValidationResult {
        validateEmail(email).let { result ->
            if (result is ValidationResult.Invalid) return result
        }
        
        validatePassword(password).let { result ->
            if (result is ValidationResult.Invalid) return result
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Valida credenciales de registro (email, contraseña y confirmación)
     */
    fun validateRegisterCredentials(
        email: String,
        password: String,
        confirmPassword: String
    ): ValidationResult {
        validateEmail(email).let { result ->
            if (result is ValidationResult.Invalid) return result
        }
        
        validatePassword(password).let { result ->
            if (result is ValidationResult.Invalid) return result
        }
        
        validatePasswordMatch(password, confirmPassword).let { result ->
            if (result is ValidationResult.Invalid) return result
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Obtiene el mensaje de error localizado
     */
    fun getErrorMessage(error: ValidationError): String {
        return when (error) {
            ValidationError.EMPTY_EMAIL -> "El email es requerido"
            ValidationError.INVALID_EMAIL_FORMAT -> "Formato de email inválido"
            ValidationError.EMPTY_PASSWORD -> "La contraseña es requerida"
            ValidationError.WEAK_PASSWORD -> "La contraseña debe tener al menos 6 caracteres"
            ValidationError.PASSWORDS_DO_NOT_MATCH -> "Las contraseñas no coinciden"
        }
    }
}
