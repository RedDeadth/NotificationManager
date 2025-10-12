package com.dynamictecnologies.notificationmanager.data.validator

/**
 * Validador de nombres de usuario siguiendo el principio de responsabilidad única (SRP).
 * Extraído de UserViewModel y UserService para aplicar DRY.
 * 
 * Principios aplicados:
 * - SRP: Solo valida usernames
 * - DRY: Elimina duplicación entre UserViewModel y UserService
 * - OCP: Fácil de extender con nuevas reglas de validación
 */
class UsernameValidator {
    
    companion object {
        private const val MIN_LENGTH = 3
        private const val MAX_LENGTH = 30
        private val USERNAME_REGEX = "^[a-zA-Z0-9]+$".toRegex()
    }
    
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
        EMPTY,
        TOO_SHORT,
        TOO_LONG,
        CONTAINS_SPACES,
        INVALID_CHARACTERS
    }
    
    /**
     * Valida un nombre de usuario
     */
    fun validate(username: String): ValidationResult {
        val trimmed = username.trim()
        
        return when {
            trimmed.isBlank() -> 
                ValidationResult.Invalid(ValidationError.EMPTY)
            
            trimmed.length < MIN_LENGTH -> 
                ValidationResult.Invalid(ValidationError.TOO_SHORT)
            
            trimmed.length > MAX_LENGTH -> 
                ValidationResult.Invalid(ValidationError.TOO_LONG)
            
            trimmed.contains(" ") -> 
                ValidationResult.Invalid(ValidationError.CONTAINS_SPACES)
            
            !trimmed.matches(USERNAME_REGEX) -> 
                ValidationResult.Invalid(ValidationError.INVALID_CHARACTERS)
            
            else -> ValidationResult.Valid
        }
    }
    
    /**
     * Obtiene el mensaje de error localizado
     */
    fun getErrorMessage(error: ValidationError): String {
        return when (error) {
            ValidationError.EMPTY -> 
                "El nombre de usuario no puede estar vacío"
            
            ValidationError.TOO_SHORT -> 
                "El nombre de usuario debe tener al menos $MIN_LENGTH caracteres"
            
            ValidationError.TOO_LONG -> 
                "El nombre de usuario no puede tener más de $MAX_LENGTH caracteres"
            
            ValidationError.CONTAINS_SPACES -> 
                "El nombre de usuario no puede contener espacios"
            
            ValidationError.INVALID_CHARACTERS -> 
                "El nombre de usuario solo puede contener letras y números"
        }
    }
}
