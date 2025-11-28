package com.dynamictecnologies.notificationmanager.data.validator

/**
 * Validador robusto de contraseñas que implementa mejores prácticas de seguridad.
 * 
 * Reglas de validación:
 * - Mínimo 8 caracteres
 * - Al menos una mayúscula
 * - Al menos una minúscula  
 * - Al menos un número
 * - Al menos un carácter especial
 * - No puede estar en lista de contraseñas comunes
 * 
 * Principios aplicados:
 * - SRP: Solo valida contraseñas
 * - OCP: Extensible mediante adición de reglas
 */
class PasswordValidator {
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }
    
    companion object {
        private const val MIN_LENGTH = 8
        private val SPECIAL_CHARS_REGEX = Regex("[^A-Za-z0-9]")
        
        // Lista de las contraseñas más comunes (parcial para demostración)
        private val COMMON_PASSWORDS = setOf(
            "password", "12345678", "qwerty", "abc123",
            "password123", "admin123", "letmein", "welcome",
            "monkey", "1234567890", "password1", "123123",
            "12341234", "Password1", "Password123", "admin",
            "administrator", "root", "toor", "pass",
            "passw0rd", "qwertyuiop", "asdfghjkl"
        )
    }
    
    /**
     * Valida una contraseña contra todas las reglas de seguridad
     */
    fun validate(password: String): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Regla 1: Longitud mínima
        if (password.length < MIN_LENGTH) {
            errors.add("La contraseña debe tener al menos $MIN_LENGTH caracteres")
        }
        
        // Regla 2: Al menos una mayúscula
        if (!password.any { it.isUpperCase() }) {
            errors.add("La contraseña debe contener al menos una letra mayúscula")
        }
        
        // Regla 3: Al menos una minúscula
        if (!password.any { it.isLowerCase() }) {
            errors.add("La contraseña debe contener al menos una letra minúscula")
        }
        
        // Regla 4: Al menos un número
        if (!password.any { it.isDigit() }) {
            errors.add("La contraseña debe contener al menos un número")
        }
        
        // Regla 5: Al menos un carácter especial
        if (!SPECIAL_CHARS_REGEX.containsMatchIn(password)) {
            errors.add("La contraseña debe contener al menos un carácter especial (!@#$%^&*)")
        }
        
        // Regla 6: No puede ser una contraseña común
        if (isCommonPassword(password)) {
            errors.add("Esta contraseña es demasiado común y fácil de adivinar")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Calcula la fortaleza de la contraseña (0-100)
     */
    fun calculateStrength(password: String): Int {
        var strength = 0
        
        // +25 por longitud adecuada
        if (password.length >= MIN_LENGTH) strength += 25
        if (password.length >= 12) strength += 10
        if (password.length >= 16) strength += 10
        
        // +15 por mayúsculas
        if (password.any { it.isUpperCase() }) strength += 15
        
        // +15 por minúsculas
        if (password.any { it.isLowerCase() }) strength += 15
        
        // +15 por números
        if (password.any { it.isDigit() }) strength += 15
        
        // +15 por caracteres especiales
        if (SPECIAL_CHARS_REGEX.containsMatchIn(password)) strength += 15
        
        // -30 si es común
        if (isCommonPassword(password)) strength -= 30
        
        return strength.coerceIn(0, 100)
    }
    
    /**
     * Verifica si la contraseña está en la lista de contraseñas comunes
     */
    private fun isCommonPassword(password: String): Boolean {
        return COMMON_PASSWORDS.contains(password.lowercase())
    }
    
    /**
     * Proporciona feedback sobre la fortaleza de la contraseña
     */
    fun getStrengthFeedback(password: String): String {
        return when (val strength = calculateStrength(password)) {
            in 0..25 -> "Muy débil"
            in 26..50 -> "Débil"
            in 51..75 -> "Media"
            in 76..90 -> "Fuerte"
            else -> "Muy fuerte"
        }
    }
}
