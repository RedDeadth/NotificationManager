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
    fun `validate accepts valid 6-character alphanumeric token`() {
        assertTrue(TokenValidator.validate("A3F9K2"))
        assertTrue(TokenValidator.validate("123456"))
        assertTrue(TokenValidator.validate("ABCDEF"))
        assertTrue(TokenValidator.validate("A1B2C3"))
    }
    
    @Test
    fun `validate rejects token shorter than 6 characters`() {
        assertFalse(TokenValidator.validate("SHORT"))
        assertFalse(TokenValidator.validate("ABC12"))
        assertFalse(TokenValidator.validate("12345"))
        assertFalse(TokenValidator.validate(""))
    }
    
    @Test
    fun `validate rejects token longer than 6 characters`() {
        assertFalse(TokenValidator.validate("TOOLONGTOKEN"))
        assertFalse(TokenValidator.validate("1234567"))
    }
    
    @Test
    fun `validate rejects token with lowercase letters`() {
        assertFalse(TokenValidator.validate("a3f9k2"))
        assertFalse(TokenValidator.validate("AbCdEf"))
        assertFalse(TokenValidator.validate("test12"))
    }
    
    @Test
    fun `validate rejects token with special characters`() {
        assertFalse(TokenValidator.validate("A3F@K2"))
        assertFalse(TokenValidator.validate("TEST-1"))
        assertFalse(TokenValidator.validate("TOK_01"))
        assertFalse(TokenValidator.validate("TES 12"))
    }
    
    @Test
    fun `formatAsTopic generates correct MQTT topic`() {
        assertEquals("n/A3F9K2", TokenValidator.formatAsTopic("A3F9K2"))
        assertEquals("n/123456", TokenValidator.formatAsTopic("123456"))
        assertEquals("n/TESTAB", TokenValidator.formatAsTopic("TESTAB"))
    }
    
    @Test
    fun `extractTokenFromTopic extracts token from valid topic`() {
        assertEquals("A3F9K2", TokenValidator.extractTokenFromTopic("n/A3F9K2"))
        assertEquals("123456", TokenValidator.extractTokenFromTopic("n/123456"))
    }
    
    @Test
    fun `extractTokenFromTopic returns null for invalid topic`() {
        assertEquals(null, TokenValidator.extractTokenFromTopic("invalid"))
        assertEquals(null, TokenValidator.extractTokenFromTopic("n/SHORT"))
        assertEquals(null, TokenValidator.extractTokenFromTopic("n/TOOLONGTOKEN"))
        assertEquals(null, TokenValidator.extractTokenFromTopic(""))
    }
    
    @Test
    fun `TOKEN_LENGTH constant is 6`() {
        assertEquals(6, TokenValidator.TOKEN_LENGTH)
    }
}
