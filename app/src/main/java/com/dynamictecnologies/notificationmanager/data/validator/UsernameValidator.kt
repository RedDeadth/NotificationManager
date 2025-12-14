package com.dynamictecnologies.notificationmanager.data.validator

import com.dynamictecnologies.notificationmanager.data.constants.AuthStrings

/**
 * Validador de nombres de usuario siguiendo el principio de responsabilidad única (SRP).
 * Extraído de UserViewModel y UserService para aplicar DRY.
 * 
 */
class UsernameValidator {
    
    companion object {
        private const val MIN_LENGTH = 3
        private const val MAX_LENGTH = 30
        private val USERNAME_REGEX = "^[a-zA-Z0-9]+$".toRegex()
    }
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val error: ValidationError) : ValidationResult()
    }
    
    enum class ValidationError {
        EMPTY,
        TOO_SHORT,
        TOO_LONG,
        CONTAINS_SPACES,
        INVALID_CHARACTERS
    }
    
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
    
    fun getErrorMessage(error: ValidationError): String {
        return when (error) {
            ValidationError.EMPTY -> 
                AuthStrings.ValidationErrors.EMPTY_USERNAME
            
            ValidationError.TOO_SHORT -> 
                String.format(AuthStrings.ValidationErrors.USERNAME_TOO_SHORT, MIN_LENGTH)
            
            ValidationError.TOO_LONG -> 
                String.format(AuthStrings.ValidationErrors.USERNAME_TOO_LONG, MAX_LENGTH)
            
            ValidationError.CONTAINS_SPACES -> 
                AuthStrings.ValidationErrors.USERNAME_CONTAINS_SPACES
            
            ValidationError.INVALID_CHARACTERS -> 
                AuthStrings.ValidationErrors.USERNAME_INVALID_CHARACTERS
        }
    }
}
