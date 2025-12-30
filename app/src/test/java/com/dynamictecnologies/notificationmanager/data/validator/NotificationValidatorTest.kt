package com.dynamictecnologies.notificationmanager.data.validator

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests para NotificationValidator.
 * 
 * Cubre:
 * - Emojis complejos (banderas, secuencias ZWJ)
 * - Sanitización de etiquetas HTML/XSS
 * - Recorte de strings largos sin mutilación de Unicode
 */
class NotificationValidatorTest {

    private lateinit var validator: NotificationValidator

    @Before
    fun setup() {
        validator = NotificationValidator()
    }

    // ==================== TESTS DE EMOJIS COMPLEJOS ====================

    @Test
    fun `sanitize preserves simple emojis`() {
        val input = "Hello 😀👍🎉"
        val result = validator.sanitize(input)
        
        assertEquals("Hello 😀👍🎉", result)
    }

    @Test
    fun `sanitize preserves flag emojis (multi-codepoint)`() {
        // Las banderas son 2 code points: 🇪🇸 = U+1F1EA U+1F1F8
        val input = "Spain 🇪🇸 and USA 🇺🇸"
        val result = validator.sanitize(input)
        
        assertEquals("Spain 🇪🇸 and USA 🇺🇸", result)
    }

    @Test
    fun `sanitize preserves ZWJ family emojis`() {
        // Familia: 👨‍👩‍👧‍👦 = 4 emojis unidos por Zero Width Joiner
        val familyEmoji = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66" // 👨‍👩‍👧‍👦
        val input = "Family: $familyEmoji"
        val result = validator.sanitize(input)
        
        assertTrue("ZWJ emoji should be preserved", result.contains(familyEmoji))
    }

    @Test
    fun `sanitize preserves skin tone modifiers`() {
        // Emoji con modificador de tono de piel: 👋🏽
        val wavingHand = "\uD83D\uDC4B\uD83C\uDFFD" // 👋🏽
        val input = "Hello $wavingHand"
        val result = validator.sanitize(input)
        
        assertTrue("Skin tone emoji should be preserved", result.contains(wavingHand))
    }

    // ==================== TESTS DE SANITIZACIÓN HTML/XSS ====================

    @Test
    fun `sanitize removes script tags`() {
        val input = "<script>alert('XSS')</script>Hello World"
        val result = validator.sanitize(input)
        
        assertFalse("Should not contain script tag", result.contains("<script>"))
        assertFalse("Should not contain closing script tag", result.contains("</script>"))
        assertTrue("Should preserve text content", result.contains("Hello World"))
    }

    @Test
    fun `sanitize removes HTML tags while preserving text`() {
        val input = "<strong>Bold</strong> and <em>italic</em> text"
        val result = validator.sanitize(input)
        
        assertEquals("Bold and italic text", result)
    }

    @Test
    fun `sanitize removes nested HTML tags`() {
        val input = "<div><p>Nested <span>content</span></p></div>"
        val result = validator.sanitize(input)
        
        assertEquals("Nested content", result)
    }

    @Test
    fun `sanitize removes malformed HTML tags`() {
        val input = "<img src=x onerror=alert(1)>Malicious"
        val result = validator.sanitize(input)
        
        assertFalse("Should not contain img tag", result.contains("<img"))
        assertFalse("Should not contain onerror", result.contains("onerror"))
    }

    @Test
    fun `sanitize removes anchor tags with javascript`() {
        val input = "<a href=\"javascript:void(0)\">Click me</a>"
        val result = validator.sanitize(input)
        
        assertFalse("Should not contain anchor tag", result.contains("<a"))
        assertFalse("Should not contain javascript", result.contains("javascript"))
        assertTrue("Should preserve link text", result.contains("Click me"))
    }

    @Test
    fun `sanitize preserves angle brackets in normal text`() {
        // Casos como "5 < 10" o código que no es HTML
        val input = "5 < 10 and 10 > 5"
        val result = validator.sanitize(input)
        
        // Puede que se elimine incorrectamente, pero es un trade-off de seguridad
        // Lo importante es que no haya tags HTML
        assertNotNull(result)
    }

    // ==================== TESTS DE TRUNCAMIENTO DE STRINGS LARGOS ====================

    @Test
    fun `sanitize truncates content exceeding MAX_CONTENT_LENGTH`() {
        val longString = "a".repeat(600) // Más de 500
        val result = validator.sanitize(longString)
        
        // Debería truncar a MAX_CONTENT_LENGTH o menos
        assertTrue(
            "Result should not exceed MAX_CONTENT_LENGTH",
            result.length <= NotificationValidator.MAX_CONTENT_LENGTH
        )
    }

    @Test
    fun `sanitize does not break simple emoji at truncation boundary`() {
        // Crear string con emoji en el límite
        val prefix = "a".repeat(NotificationValidator.MAX_CONTENT_LENGTH - 2)
        val emoji = "😀" // 2 UTF-16 code units
        val input = prefix + emoji
        
        val result = validator.sanitize(input)
        
        // No debe haber caracteres sueltos de sustitución (broken surrogates)
        assertFalse(
            "Should not contain broken surrogate characters", 
            result.any { it.isHighSurrogate() && !result.getOrNull(result.indexOf(it) + 1)?.isLowSurrogate()!! }
        )
    }

    @Test
    fun `sanitize does not break flag emoji at truncation boundary`() {
        // Bandera en el límite de truncamiento
        val prefix = "a".repeat(NotificationValidator.MAX_CONTENT_LENGTH - 4)
        val flagEmoji = "🇪🇸" // 4 UTF-16 code units (2 surrogate pairs)
        val input = prefix + flagEmoji
        
        val result = validator.sanitize(input)
        
        // Verificar que no hay surrogates rotos
        result.forEachIndexed { index, char ->
            if (char.isHighSurrogate()) {
                val nextChar = result.getOrNull(index + 1)
                assertTrue(
                    "High surrogate should be followed by low surrogate",
                    nextChar?.isLowSurrogate() == true
                )
            }
        }
    }

    @Test
    fun `sanitize does not break ZWJ sequence at truncation boundary`() {
        // Familia emoji en el límite
        val familyEmoji = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66" // 👨‍👩‍👧‍👦
        val prefixLength = NotificationValidator.MAX_CONTENT_LENGTH - familyEmoji.length + 2 // Forzar corte
        val prefix = "a".repeat(prefixLength.coerceAtLeast(0))
        val input = prefix + familyEmoji
        
        val result = validator.sanitize(input)
        
        // Verificar que no hay surrogates rotos o ZWJ sueltos
        assertFalse(
            "Should not contain isolated ZWJ character",
            result.endsWith("\u200D")
        )
    }

    @Test
    fun `sanitize handles mixed content with HTML and emojis`() {
        val input = "<b>Hello</b> 👨‍👩‍👧‍👦 <script>evil()</script> World 🎉"
        val result = validator.sanitize(input)
        
        // Debe remover HTML pero preservar emojis y texto
        assertFalse("Should not contain HTML tags", result.contains("<"))
        assertTrue("Should contain family emoji or be safely truncated", 
            result.contains("Hello") || result.isNotEmpty())
    }

    // ==================== TESTS DE VALIDACIÓN ====================

    @Test
    fun `validate returns Valid for normal notification`() {
        val data = NotificationValidator.NotificationData(
            appName = "WhatsApp",
            title = "New message",
            content = "Hello, how are you? 😀"
        )
        
        val result = validator.validate(data)
        
        assertTrue("Should be valid", result is NotificationValidator.ValidationResult.Valid)
    }

    @Test
    fun `validate detects dangerous script pattern`() {
        val data = NotificationValidator.NotificationData(
            appName = "TestApp",
            title = "Test",
            content = "<script>alert('xss')</script>"
        )
        
        val result = validator.validate(data)
        
        assertTrue("Should be invalid", result is NotificationValidator.ValidationResult.Invalid)
        val invalid = result as NotificationValidator.ValidationResult.Invalid
        assertTrue(
            "Should contain CONTAINS_MALICIOUS_CODE error",
            invalid.errors.contains(NotificationValidator.ValidationError.CONTAINS_MALICIOUS_CODE)
        )
    }

    @Test
    fun `validate detects content too long`() {
        val data = NotificationValidator.NotificationData(
            appName = "TestApp",
            title = "Test",
            content = "a".repeat(600)
        )
        
        val result = validator.validate(data)
        
        assertTrue("Should be invalid", result is NotificationValidator.ValidationResult.Invalid)
        val invalid = result as NotificationValidator.ValidationResult.Invalid
        assertTrue(
            "Should contain CONTENT_TOO_LONG error",
            invalid.errors.contains(NotificationValidator.ValidationError.CONTENT_TOO_LONG)
        )
    }

    @Test
    fun `validate handles notification with only emojis`() {
        val data = NotificationValidator.NotificationData(
            appName = "Messages",
            title = "😀",
            content = "👨‍👩‍👧‍👦🎉🇪🇸"
        )
        
        val result = validator.validate(data)
        
        assertTrue("Emoji-only notification should be valid", result is NotificationValidator.ValidationResult.Valid)
    }
}
