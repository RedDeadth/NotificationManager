package com.dynamictecnologies.notificationmanager.data.validator

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PasswordValidatorTest {

    private lateinit var passwordValidator: PasswordValidator

    @Before
    fun setup() {
        passwordValidator = PasswordValidator()
    }

    // ========== Password Length Tests ==========

    @Test
    fun `validate returns Invalid when password is too short`() {
        val result = passwordValidator.validate("Pass1")  // Less than 8 chars

        assertTrue(result is PasswordValidator.ValidationResult.Invalid)
        val errors = (result as PasswordValidator.ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("8 caracteres") })
    }

    @Test
    fun `validate returns Valid when password meets minimum length`() {
        val result = passwordValidator.validate("Passw0rd!")  // Exactly 9 chars with special char

        assertTrue(result is PasswordValidator.ValidationResult.Valid)
    }

    // ========== Uppercase Tests ==========

    @Test
    fun `validate returns Invalid when password has no uppercase`() {
        val result = passwordValidator.validate("password123")

        assertTrue(result is PasswordValidator.ValidationResult.Invalid)
        val errors = (result as PasswordValidator.ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("mayúscula") })
    }

    // ========== Lowercase Tests ==========

    @Test
    fun `validate returns Invalid when password has no lowercase`() {
        val result = passwordValidator.validate("PASSWORD123")

        assertTrue(result is PasswordValidator.ValidationResult.Invalid)
        val errors = (result as PasswordValidator.ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("minúscula") })
    }

    // ========== Digit Tests ==========

    @Test
    fun `validate returns Invalid when password has no digit`() {
        val result = passwordValidator.validate("Password")

        assertTrue(result is PasswordValidator.ValidationResult.Invalid)
        val errors = (result as PasswordValidator.ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("número") })
    }

    // ========== Multiple Validation Errors Tests ==========

    @Test
    fun `validate returns multiple errors when password fails multiple rules`() {
        val result = passwordValidator.validate("pass")  // Too short, no uppercase, no digit

        assertTrue(result is PasswordValidator.ValidationResult.Invalid)
        val errors = (result as PasswordValidator.ValidationResult.Invalid).errors
        assertTrue(errors.size >= 3)
    }

    // ========== Valid Password Tests ==========

    @Test
    fun `validate returns Valid when password meets all requirements`() {
        val validPasswords = listOf(
            "P@ssword123",
            "SecureP@ss1",
            "MyP@ssw0rd",
            "Test#1234",
            "Abcdef!123",
            "ValidPass99!"
        )

        validPasswords.forEach { password ->
            val result = passwordValidator.validate(password)
            assertTrue("Failed for password: $password", result is PasswordValidator.ValidationResult.Valid)
        }
    }

    @Test
    fun `validate returns Valid for password with special characters`() {
        val result = passwordValidator.validate("P@ssw0rd!")

        assertTrue(result is PasswordValidator.ValidationResult.Valid)
    }

    @Test
    fun `validate returns Valid for long password`() {
        val result = passwordValidator.validate("ThisIsALongPassword123!")

        assertTrue(result is PasswordValidator.ValidationResult.Valid)
    }

    // ========== Edge Cases Tests ==========

    @Test
    fun `validate handles password with only uppercase and digits`() {
        val result = passwordValidator.validate("PASSWORD123")

        assertTrue(result is PasswordValidator.ValidationResult.Invalid)
        val errors = (result as PasswordValidator.ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("minúscula") })
    }

    @Test
    fun `validate handles password with only lowercase and digits`() {
        val result = passwordValidator.validate("password123")

        assertTrue(result is PasswordValidator.ValidationResult.Invalid)
        val errors = (result as PasswordValidator.ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("mayúscula") })
    }

    @Test
    fun `validate handles password with only letters`() {
        val result = passwordValidator.validate("Password")

        assertTrue(result is PasswordValidator.ValidationResult.Invalid)
        val errors = (result as PasswordValidator.ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("número") })
    }
}
