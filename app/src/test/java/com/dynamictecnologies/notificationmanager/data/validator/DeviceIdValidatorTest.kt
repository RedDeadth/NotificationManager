package com.dynamictecnologies.notificationmanager.data.validator

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests para DeviceIdValidator.
 * 
 * Cubre:
 * - Formatos válidos
 * - Formatos inválidos
 * - Edge cases (vacío, muy largo, caracteres especiales)
 * - Sanitización
 */
class DeviceIdValidatorTest {
    
    private lateinit var validator: DeviceIdValidator
    
    @Before
    fun setup() {
        validator = DeviceIdValidator()
    }
    
    // ========== TESTS DE VALIDACIÓN ==========
    
    @Test
    fun `validates correct device format`() {
        val validIds = listOf(
            "device123",
            "esp32_456",
            "DEVICE-789",
            "device_abc123",
            "esp32123"
        )
        
        validIds.forEach { id ->
            val result = validator.validate(id)
            assertTrue("$id should be valid", result is DeviceIdValidator.ValidationResult.Valid)
        }
    }
    
    @Test
    fun `rejects empty device id`() {
        val result = validator.validate("")
        assertTrue(result is DeviceIdValidator.ValidationResult.Invalid)
        val error = (result as DeviceIdValidator.ValidationResult.Invalid).error
        assertEquals(DeviceIdValidator.ValidationError.EMPTY, error)
    }
    
    @Test
    fun `rejects device id too short`() {
        val result = validator.validate("dev")
        assertTrue(result is DeviceIdValidator.ValidationResult.Invalid)
        val error = (result as DeviceIdValidator.ValidationResult.Invalid).error
        assertEquals(DeviceIdValidator.ValidationError.TOO_SHORT, error)
    }
    
    @Test
    fun `rejects device id too long`() {
        val longId = "device" + "1".repeat(50)
        val result = validator.validate(longId)
        assertTrue(result is DeviceIdValidator.ValidationResult.Invalid)
        val error = (result as DeviceIdValidator.ValidationResult.Invalid).error
        assertEquals(DeviceIdValidator.ValidationError.TOO_LONG, error)
    }
    
    @Test
    fun `rejects invalid format - no prefix`() {
        val result = validator.validate("random123")
        assertTrue(result is DeviceIdValidator.ValidationResult.Invalid)
        val error = (result as DeviceIdValidator.ValidationResult.Invalid).error
        assertEquals(DeviceIdValidator.ValidationError.INVALID_FORMAT, error)
    }
    
    @Test
    fun `rejects device id with special characters`() {
        val invalidIds = listOf(
            "device@123",
            "esp32#456",
            "device 789",
            "device;123",
            "device<script>",
            "device'OR'1'='1"
        )
        
        invalidIds.forEach { id ->
            val result = validator.validate(id)
            assertTrue("$id should be rejected", result is DeviceIdValidator.ValidationResult.Invalid)
        }
    }
    
    @Test
    fun `rejects SQL injection attempts`() {
        val maliciousIds = listOf(
            "device'; DROP TABLE devices--",
            "device' OR '1'='1",
            "device<script>alert('xss')</script>"
        )
        
        maliciousIds.forEach { id ->
            val result = validator.validate(id)
            assertTrue("$id should be rejected", result is DeviceIdValidator.ValidationResult.Invalid)
        }
    }
    
    @Test
    fun `rejects emojis in device id`() {
        val result = validator.validate("device😀123")
        assertTrue(result is DeviceIdValidator.ValidationResult.Invalid)
    }
    
    // ========== TESTS DE SANITIZACIÓN ==========
    
    @Test
    fun `sanitize removes dangerous characters`() {
        val malicious = "device<script>123</script>"
        val sanitized = validator.sanitize(malicious)
        
        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
        assertTrue(sanitized.matches(Regex("[a-zA-Z0-9_-]+")))
    }
    
    @Test
    fun `sanitize trims whitespace`() {
        val withSpaces = "  device123  "
        val sanitized = validator.sanitize(withSpaces)
        
        assertEquals("device123", sanitized)
    }
    
    @Test
    fun `sanitize enforces max length`() {
        val tooLong = "device" + "1".repeat(50)
        val sanitized = validator.sanitize(tooLong)
        
        assertTrue(sanitized.length <= 30)
    }
    
    // ========== TESTS DE EDGE CASES ==========
    
    @Test
    fun `handles null-like strings gracefully`() {
        val nullLikes = listOf("null", "NULL", "undefined", "none")
        
        nullLikes.forEach { id ->
            val result = validator.validate(id)
            // Should be invalid due to format
            assertTrue(result is DeviceIdValidator.ValidationResult.Invalid)
        }
    }
    
    @Test
    fun `case insensitive prefix matching`() {
        val mixedCase = listOf(
            "Device123",
            "DEVICE123",
            "Esp32_456",
            "ESP32_456"
        )
        
        mixedCase.forEach { id ->
            val result = validator.validate(id)
            assertTrue("$id should be valid", result is DeviceIdValidator.ValidationResult.Valid)
        }
    }
    
    @Test
    fun `error messages are user friendly`() {
        val errors = DeviceIdValidator.ValidationError.values()
        
        errors.forEach { error ->
            val message = validator.getErrorMessage(error)
            assertNotNull(message)
            assertTrue(message.isNotBlank())
            assertTrue(message.length > 10) // Meaningful message
        }
    }
}
