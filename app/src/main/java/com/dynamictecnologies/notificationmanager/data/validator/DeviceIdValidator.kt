package com.dynamictecnologies.notificationmanager.data.validator

/**
 * Validador para Device IDs de dispositivos ESP32.
 * 
 * Formato esperado: "device{número}" o "esp32_{número}"
 * Ejemplos válidos: "device123", "esp32_456"
 * 
 * Principios aplicados:
 * - SRP: Solo valida Device IDs
 * - Seguridad: Previene inyección de comandos maliciosos
 */
class DeviceIdValidator {
    
    companion object {
        // Formatos permitidos: device123, esp32_456, DEVICE-789 (case insensitive)
        private val DEVICE_ID_REGEX = "^(device|esp32)[_-]?[0-9a-zA-Z]{1,20}$".toRegex(RegexOption.IGNORE_CASE)
        private const val MIN_LENGTH = 5
        private const val MAX_LENGTH = 30
    }
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val error: ValidationError) : ValidationResult()
    }
    
    enum class ValidationError {
        EMPTY,
        TOO_SHORT,
        TOO_LONG,
        INVALID_FORMAT,
        CONTAINS_SPECIAL_CHARS
    }
    
    fun validate(deviceId: String): ValidationResult {
        val trimmed = deviceId.trim()
        
        return when {
            trimmed.isBlank() -> 
                ValidationResult.Invalid(ValidationError.EMPTY)
            
            trimmed.length < MIN_LENGTH -> 
                ValidationResult.Invalid(ValidationError.TOO_SHORT)
            
            trimmed.length > MAX_LENGTH -> 
                ValidationResult.Invalid(ValidationError.TOO_LONG)
            
            !trimmed.matches(DEVICE_ID_REGEX) -> 
                ValidationResult.Invalid(ValidationError.INVALID_FORMAT)
            
            else -> ValidationResult.Valid
        }
    }
    
    fun getErrorMessage(error: ValidationError): String {
        return when (error) {
            ValidationError.EMPTY -> 
                "El ID del dispositivo no puede estar vacío"
            
            ValidationError.TOO_SHORT -> 
                "El ID del dispositivo debe tener al menos $MIN_LENGTH caracteres"
            
            ValidationError.TOO_LONG -> 
                "El ID del dispositivo no puede exceder $MAX_LENGTH caracteres"
            
            ValidationError.INVALID_FORMAT -> 
                "Formato inválido. Use: device123 o esp32_456"
            
            ValidationError.CONTAINS_SPECIAL_CHARS -> 
                "El ID solo puede contener letras, números, guiones y guiones bajos"
        }
    }
    
    /**
     * Sanitiza un device ID eliminando caracteres peligrosos.
     */
    fun sanitize(deviceId: String): String {
        return deviceId.trim()
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
            .take(MAX_LENGTH)
    }
}
