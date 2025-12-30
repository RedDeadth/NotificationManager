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
     * 
     * - Remueve etiquetas HTML
     * - Trunca a MAX_CONTENT_LENGTH respetando caracteres Unicode multi-byte
     * - Preserva emojis ZWJ, banderas, y variantes
     */
    fun sanitize(text: String): String {
        // Primero remover HTML tags
        val htmlSanitized = text.replace(Regex("<[^>]*>"), "").trim()
        
        // Truncar de forma segura para Unicode
        return truncateUnicodeSafe(htmlSanitized, MAX_CONTENT_LENGTH)
    }
    
    /**
     * Trunca un string respetando límites de caracteres Unicode.
     * 
     * Evita cortar:
     * - Surrogate pairs (emojis básicos)
     * - Secuencias ZWJ (emojis compuestos como 👨‍👩‍👧‍👦)
     * - Regional indicators (banderas como 🇪🇸)
     * - Skin tone modifiers
     */
    private fun truncateUnicodeSafe(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        
        // Usar code points en lugar de chars para manejo correcto de Unicode
        val codePoints = text.codePoints().toArray()
        
        var charCount = 0
        var codePointIndex = 0
        
        // Contar hasta encontrar el límite
        while (codePointIndex < codePoints.size && charCount < maxLength) {
            val codePoint = codePoints[codePointIndex]
            val charCountForCodePoint = Character.charCount(codePoint)
            
            // Si agregar este code point excede el límite, parar
            if (charCount + charCountForCodePoint > maxLength) {
                break
            }
            
            charCount += charCountForCodePoint
            codePointIndex++
        }
        
        // Retroceder si terminamos en medio de una secuencia ZWJ o modificador
        // SEGURIDAD: Contador para evitar loop infinito con strings maliciosos
        var safetyCounter = 0
        val MAX_BACKTRACK_ITERATIONS = 100
        
        while (codePointIndex > 0 && safetyCounter < MAX_BACKTRACK_ITERATIONS) {
            safetyCounter++
            val prevCodePoint = codePoints[codePointIndex - 1]
            
            // Verificar si el último code point es un ZWJ (Zero Width Joiner)
            // que indica una secuencia compuesta incompleta
            if (prevCodePoint == 0x200D) {
                codePointIndex--
                continue
            }
            
            // Verificar si es un Regional Indicator (banderas)
            // Las banderas vienen en pares, no cortar en medio
            if (isRegionalIndicator(prevCodePoint) && codePointIndex >= 2) {
                val beforePrev = codePoints[codePointIndex - 2]
                // Si el anterior también es regional indicator, están emparejados, OK
                if (!isRegionalIndicator(beforePrev)) {
                    // Hay un indicador regional suelto, retroceder
                    codePointIndex--
                    continue
                }
            }
            
            // Verificar si es un modificador (skin tone, emoji variation)
            if (isEmojiModifier(prevCodePoint) || isVariationSelector(prevCodePoint)) {
                // Los modificadores siempre van después de su base, verificar
                if (codePointIndex >= 2) {
                    codePointIndex--
                    continue
                }
            }
            
            break
        }
        
        // Construir el string truncado desde code points
        val result = StringBuilder()
        for (i in 0 until codePointIndex) {
            result.appendCodePoint(codePoints[i])
        }
        
        return result.toString()
    }
    
    /**
     * Verifica si un code point es un Regional Indicator (para banderas).
     * Rango: U+1F1E6 a U+1F1FF
     */
    private fun isRegionalIndicator(codePoint: Int): Boolean {
        return codePoint in 0x1F1E6..0x1F1FF
    }
    
    /**
     * Verifica si un code point es un Emoji Modifier (skin tones).
     * Rango: U+1F3FB a U+1F3FF (Fitzpatrick modifiers)
     */
    private fun isEmojiModifier(codePoint: Int): Boolean {
        return codePoint in 0x1F3FB..0x1F3FF
    }
    
    /**
     * Verifica si un code point es un Variation Selector.
     * U+FE0F (emoji style) o U+FE0E (text style)
     */
    private fun isVariationSelector(codePoint: Int): Boolean {
        return codePoint == 0xFE0F || codePoint == 0xFE0E
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
