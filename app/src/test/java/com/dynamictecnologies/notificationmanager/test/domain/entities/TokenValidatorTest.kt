package com.dynamictecnologies.notificationmanager.test.domain.entities

import com.dynamictecnologies.notificationmanager.domain.entities.TokenValidator
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para TokenValidator.
 * 
 * Verifica:
 * - Validación correcta de tokens
 * - Generación de topics MQTT
 * - Extracción de tokens de topics
 * - Casos edge
 */
class TokenValidatorTest {
    
    @Test
    fun `validate accepts valid 8-character alphanumeric token`() {
        assertTrue(TokenValidator.validate("A3F9K2L7"))
        assertTrue(TokenValidator.validate("12345678"))
        assertTrue(TokenValidator.validate("ABCDEFGH"))
        assertTrue(TokenValidator.validate("A1B2C3D4"))
    }
    
    @Test
    fun `validate rejects token shorter than 8 characters`() {
        assertFalse(TokenValidator.validate("SHORT"))
        assertFalse(TokenValidator.validate("ABC123"))
        assertFalse(TokenValidator.validate("1234567"))
        assertFalse(TokenValidator.validate(""))
    }
    
    @Test
    fun `validate rejects token longer than 8 characters`() {
        assertFalse(TokenValidator.validate("TOOLONGTOKEN"))
        assertFalse(TokenValidator.validate("123456789"))
    }
    
    @Test
    fun `validate rejects token with lowercase letters`() {
        assertFalse(TokenValidator.validate("a3f9k2l7"))
        assertFalse(TokenValidator.validate("AbCdEfGh"))
        assertFalse(TokenValidator.validate("test1234"))
    }
    
    @Test
    fun `validate rejects token with special characters`() {
        assertFalse(TokenValidator.validate("A3F@K2L7"))
        assertFalse(TokenValidator.validate("TEST-123"))
        assertFalse(TokenValidator.validate("TOKEN_01"))
        assertFalse(TokenValidator.validate("TEST 123"))
    }
    
    @Test
    fun `formatAsTopic generates correct MQTT topic`() {
        assertEquals("n/A3F9K2L7", TokenValidator.formatAsTopic("A3F9K2L7"))
        assertEquals("n/12345678", TokenValidator.formatAsTopic("12345678"))
        assertEquals("n/TESTTEST", TokenValidator.formatAsTopic("TESTTEST"))
    }
    
    @Test
    fun `extractTokenFromTopic extracts token from valid topic`() {
        assertEquals("A3F9K2L7", TokenValidator.extractTokenFromTopic("n/A3F9K2L7"))
        assertEquals("12345678", TokenValidator.extractTokenFromTopic("n/12345678"))
    }
    
    @Test
    fun `extractTokenFromTopic returns null for invalid topic`() {
        assertEquals(null, TokenValidator.extractTokenFromTopic("invalid"))
        assertEquals(null, TokenValidator.extractTokenFromTopic("n/SHORT"))
        assertEquals(null, TokenValidator.extractTokenFromTopic("n/TOOLONGTOKEN"))
        assertEquals(null, TokenValidator.extractTokenFromTopic(""))
    }
    
    @Test
    fun `TOKEN_LENGTH constant is 8`() {
        assertEquals(8, TokenValidator.TOKEN_LENGTH)
    }
}
