package com.dynamictecnologies.notificationmanager.data.validator

import com.dynamictecnologies.notificationmanager.data.constants.AuthStrings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UsernameValidatorTest {

    private lateinit var usernameValidator: UsernameValidator

    @Before
    fun setup() {
        usernameValidator = UsernameValidator()
    }

    // ========== Username Validation Tests ==========

    @Test
    fun `validate returns Invalid when username is empty`() {
        val result = usernameValidator.validate("")

        assertTrue(result is UsernameValidator.ValidationResult.Invalid)
        assertEquals(
            UsernameValidator.ValidationError.EMPTY,
            (result as UsernameValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validate returns Invalid when username is blank`() {
        val result = usernameValidator.validate("   ")

        assertTrue(result is UsernameValidator.ValidationResult.Invalid)
        assertEquals(
            UsernameValidator.ValidationError.EMPTY,
            (result as UsernameValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validate returns Invalid when username is too short`() {
        val result = usernameValidator.validate("ab")

        assertTrue(result is UsernameValidator.ValidationResult.Invalid)
        assertEquals(
            UsernameValidator.ValidationError.TOO_SHORT,
            (result as UsernameValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validate returns Invalid when username is too long`() {
        val longUsername = "a".repeat(31)  // Max is 30
        val result = usernameValidator.validate(longUsername)

        assertTrue(result is UsernameValidator.ValidationResult.Invalid)
        assertEquals(
            UsernameValidator.ValidationError.TOO_LONG,
            (result as UsernameValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validate returns Invalid when username contains spaces`() {
        val result = usernameValidator.validate("user name")

        assertTrue(result is UsernameValidator.ValidationResult.Invalid)
        assertEquals(
            UsernameValidator.ValidationError.CONTAINS_SPACES,
            (result as UsernameValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validate returns Invalid when username contains invalid characters`() {
        val invalidUsernames = listOf(
            "user@name",
            "user#name",
            "user.name",
            "user-name",
            "user_name",
            "user!name",
            "üsername"
        )

        invalidUsernames.forEach { username ->
            val result = usernameValidator.validate(username)
            
            assertTrue("Failed for username: $username", result is UsernameValidator.ValidationResult.Invalid)
            assertEquals(
                UsernameValidator.ValidationError.INVALID_CHARACTERS,
                (result as UsernameValidator.ValidationResult.Invalid).error
            )
        }
    }

    @Test
    fun `validate returns Valid when username is valid lowercase`() {
        val result = usernameValidator.validate("testuser")

        assertTrue(result is UsernameValidator.ValidationResult.Valid)
    }

    @Test
    fun `validate returns Valid when username is valid uppercase`() {
        val result = usernameValidator.validate("TESTUSER")

        assertTrue(result is UsernameValidator.ValidationResult.Valid)
    }

    @Test
    fun `validate returns Valid when username contains numbers`() {
        val result = usernameValidator.validate("testuser123")

        assertTrue(result is UsernameValidator.ValidationResult.Valid)
    }

    @Test
    fun `validate returns Valid when username is at minimum length`() {
        val result = usernameValidator.validate("abc")  // Min is 3

        assertTrue(result is UsernameValidator.ValidationResult.Valid)
    }

    @Test
    fun `validate returns Valid when username is at maximum length`() {
        val maxUsername = "a".repeat(30)  // Max is 30
        val result = usernameValidator.validate(maxUsername)

        assertTrue(result is UsernameValidator.ValidationResult.Valid)
    }

    // ========== Error Message Tests ==========

    @Test
    fun `getErrorMessage returns correct message for EMPTY`() {
        val message = usernameValidator.getErrorMessage(
            UsernameValidator.ValidationError.EMPTY
        )
        
        assertEquals(AuthStrings.ValidationErrors.EMPTY_USERNAME, message)
    }

    @Test
    fun `getErrorMessage returns correct message for TOO_SHORT`() {
        val message = usernameValidator.getErrorMessage(
            UsernameValidator.ValidationError.TOO_SHORT
        )
        
        assertTrue(message.contains("al menos 3"))
    }

    @Test
    fun `getErrorMessage returns correct message for CONTAINS_SPACES`() {
        val message = usernameValidator.getErrorMessage(
            UsernameValidator.ValidationError.CONTAINS_SPACES
        )
        
        assertTrue(message.contains("espacios"))
    }

    @Test
    fun `getErrorMessage returns correct message for INVALID_CHARACTERS`() {
        val message = usernameValidator.getErrorMessage(
            UsernameValidator.ValidationError.INVALID_CHARACTERS
        )
        
        assertTrue(message.contains("letras y números"))
    }
}
