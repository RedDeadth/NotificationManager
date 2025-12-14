package com.dynamictecnologies.notificationmanager.data.validator

/**
 * Validador para contenido de notificaciones.
 * 
 * Valida:
 * - Longitud máxima de título y contenido
 * - Caracteres permitidos
 * - Previene inyección de código malicioso
 * 
 * - Seguridad: Sanitización de contenido
 */
class NotificationValidator {
    
    companion object {
        const val MAX_TITLE_LENGTH = 100
        const val MAX_CONTENT_LENGTH = 500
        const val MAX_APP_NAME_LENGTH = 100
        
        // Detecta intentos de inyección HTML/JS
        private val DANGEROUS_PATTERNS = listOf(
            Regex("<script", RegexOption.IGNORE_CASE),
            Regex("javascript:", RegexOption.IGNORE_CASE),
            Regex("onerror=", RegexOption.IGNORE_CASE),
            Regex("onclick=", RegexOption.IGNORE_CASE)
        )
    }
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<ValidationError>) : ValidationResult()
    }
    
    enum class ValidationError {
        TITLE_TOO_LONG,
        CONTENT_TOO_LONG,
        APP_NAME_TOO_LONG,
        CONTAINS_MALICIOUS_CODE,
        INVALID_CHARACTERS
    }
    
    data class NotificationData(
        val appName: String,
        val title: String,
        val content: String
    )
    
    fun validate(data: NotificationData): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        
        // Validar longitud del título
        if (data.title.length > MAX_TITLE_LENGTH) {
            errors.add(ValidationError.TITLE_TOO_LONG)
        }
        
        // Validar longitud del contenido
        if (data.content.length > MAX_CONTENT_LENGTH) {
            errors.add(ValidationError.CONTENT_TOO_LONG)
        }
        
        // Validar longitud del nombre de app
        if (data.appName.length > MAX_APP_NAME_LENGTH) {
            errors.add(ValidationError.APP_NAME_TOO_LONG)
        }
        
        // Detectar patrones peligrosos
        val allText = "${data.title} ${data.content} ${data.appName}"
        if (containsDangerousPattern(allText)) {
            errors.add(ValidationError.CONTAINS_MALICIOUS_CODE)
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    private fun containsDangerousPattern(text: String): Boolean {
        return DANGEROUS_PATTERNS.any { pattern ->
            pattern.containsMatchIn(text)
        }
    }
    
    /**
     * Sanitiza el contenido de una notificación.
     */
    fun sanitize(text: String): String {
        return text
            .take(MAX_CONTENT_LENGTH)
            .replace(Regex("<[^>]*>"), "") // Remove HTML tags
            .trim()
    }
    
    fun getErrorMessage(error: ValidationError): String {
        return when (error) {
            ValidationError.TITLE_TOO_LONG -> 
                "El título no puede exceder $MAX_TITLE_LENGTH caracteres"
            
            ValidationError.CONTENT_TOO_LONG -> 
                "El contenido no puede exceder $MAX_CONTENT_LENGTH caracteres"
            
            ValidationError.APP_NAME_TOO_LONG -> 
                "El nombre de la app no puede exceder $MAX_APP_NAME_LENGTH caracteres"
            
            ValidationError.CONTAINS_MALICIOUS_CODE -> 
                "El contenido contiene patrones sospechosos"
            
            ValidationError.INVALID_CHARACTERS -> 
                "El contenido contiene caracteres inválidos"
        }
    }
}
