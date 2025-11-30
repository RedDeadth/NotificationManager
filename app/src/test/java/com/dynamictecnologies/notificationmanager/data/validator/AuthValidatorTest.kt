package com.dynamictecnologies.notificationmanager.data.validator

import com.dynamictecnologies.notificationmanager.data.constants.AuthStrings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthValidatorTest {

    private lateinit var authValidator: AuthValidator
    private lateinit var passwordValidator: PasswordValidator
    private lateinit var usernameValidator: UsernameValidator

    @Before
    fun setup() {
        passwordValidator = PasswordValidator()
        usernameValidator = UsernameValidator()
        authValidator = AuthValidator(passwordValidator, usernameValidator)
    }

    // ========== Email Validation Tests ==========

    @Test
    fun `validateEmail returns Invalid when email is empty`() {
        val result = authValidator.validateEmail("")

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.EMPTY_EMAIL,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validateEmail returns Invalid when email is blank`() {
        val result = authValidator.validateEmail("   ")

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.EMPTY_EMAIL,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validateEmail returns Invalid when email format is invalid`() {
        val invalidEmails = listOf(
            "invalid",
            "invalid@",
            "@invalid.com",
            "invalid@com",
            "invalid.com",
            "invalid @test.com"
        )

        invalidEmails.forEach { email ->
            val result = authValidator.validateEmail(email)
            
            assertTrue("Failed for email: $email", result is AuthValidator.ValidationResult.Invalid)
            assertEquals(
                AuthValidator.ValidationError.INVALID_EMAIL_FORMAT,
                (result as AuthValidator.ValidationResult.Invalid).error
            )
        }
    }

    @Test
    fun `validateEmail returns Valid when email format is correct`() {
        val validEmails = listOf(
            "test@example.com",
            "user.name@example.com",
            "user+tag@example.co.uk",
            "test123@test-domain.com"
        )

        validEmails.forEach { email ->
            val result = authValidator.validateEmail(email)
            assertTrue("Failed for email: $email", result is AuthValidator.ValidationResult.Valid)
        }
    }

    // ========== Password Validation Tests ==========

    @Test
    fun `validatePassword returns Invalid when password is empty`() {
        val result = authValidator.validatePassword("")

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.EMPTY_PASSWORD,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validatePassword returns Invalid when password is weak`() {
        val weakPasswords = listOf(
            "short",
            "nouppercase123",
            "NOLOWERCASE123",
            "NoDigits",
            "12345678"
        )

        weakPasswords.forEach { password ->
            val result = authValidator.validatePassword(password)
            
            assertTrue("Failed for password: $password", result is AuthValidator.ValidationResult.Invalid)
            assertEquals(
                AuthValidator.ValidationError.WEAK_PASSWORD,
                (result as AuthValidator.ValidationResult.Invalid).error
            )
        }
    }

    @Test
    fun `validatePassword returns Valid when password meets all requirements`() {
        val validPasswords = listOf(
            "P@ssword123",
            "SecureP@ss1",
            "MyP@ssw0rd",
            "Test#1234"
        )

        validPasswords.forEach { password ->
            val result = authValidator.validatePassword(password)
            assertTrue("Failed for password: $password", result is AuthValidator.ValidationResult.Valid)
        }
    }

    // ========== Password Match Validation Tests ==========

    @Test
    fun `validatePasswordMatch returns Invalid when passwords do not match`() {
        val result = authValidator.validatePasswordMatch("P@ssword123", "P@ssword456")

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.PASSWORDS_DO_NOT_MATCH,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validatePasswordMatch returns Valid when passwords match`() {
        val password = "P@ssword123"
        val result = authValidator.validatePasswordMatch(password, password)

        assertTrue(result is AuthValidator.ValidationResult.Valid)
    }

    // ========== Login Credentials Validation Tests ==========

    @Test
    fun `validateLoginCredentials returns Invalid when email is empty`() {
        val result = authValidator.validateLoginCredentials("", "P@ssword123")

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.EMPTY_EMAIL,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validateLoginCredentials returns Invalid when email format is invalid`() {
        val result = authValidator.validateLoginCredentials("invalid-email", "P@ssword123")

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.INVALID_EMAIL_FORMAT,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validateLoginCredentials returns Invalid when password is empty`() {
        val result = authValidator.validateLoginCredentials("test@example.com", "")

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.EMPTY_PASSWORD,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validateLoginCredentials returns Valid when credentials are correct`() {
        val result = authValidator.validateLoginCredentials("test@example.com", "anypassword")

        assertTrue(result is AuthValidator.ValidationResult.Valid)
    }

    // ========== Registration Credentials Validation Tests ==========

    @Test
    fun `validateRegistrationCredentials returns Invalid when email is empty`() {
        val result = authValidator.validateRegistrationCredentials(
            email = "",
            password = "P@ssword123",
            confirmPassword = "P@ssword123",
            username = "testuser"
        )

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.EMPTY_EMAIL,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validateRegistrationCredentials returns Invalid when password is weak`() {
        val result = authValidator.validateRegistrationCredentials(
            email = "test@example.com",
            password = "weak",
            confirmPassword = "weak",
            username = "testuser"
        )

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.WEAK_PASSWORD,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validateRegistrationCredentials returns Invalid when passwords do not match`() {
        val result = authValidator.validateRegistrationCredentials(
            email = "test@example.com",
            password = "P@ssword123",
            confirmPassword = "P@ssword456",
            username = "testuser"
        )

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.PASSWORDS_DO_NOT_MATCH,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validateRegistrationCredentials returns Invalid when username is invalid`() {
        val result = authValidator.validateRegistrationCredentials(
            email = "test@example.com",
            password = "P@ssword123",
            confirmPassword = "P@ssword123",
            username = "ab"  // Too short
        )

        assertTrue(result is AuthValidator.ValidationResult.Invalid)
        assertEquals(
            AuthValidator.ValidationError.INVALID_USERNAME,
            (result as AuthValidator.ValidationResult.Invalid).error
        )
    }

    @Test
    fun `validateRegistrationCredentials returns Valid when all credentials are correct`() {
        val result = authValidator.validateRegistrationCredentials(
            email = "test@example.com",
            password = "P@ssword123",
            confirmPassword = "P@ssword123",
            username = "testuser"
        )

        assertTrue(result is AuthValidator.ValidationResult.Valid)
    }

    // ========== Error Message Tests ==========

    @Test
    fun `getErrorMessage returns correct message for EMPTY_EMAIL`() {
        val message = authValidator.getErrorMessage(AuthValidator.ValidationError.EMPTY_EMAIL)
        
        assertEquals(AuthStrings.ValidationErrors.EMPTY_EMAIL, message)
    }

    @Test
    fun `getErrorMessage returns correct message for WEAK_PASSWORD with details`() {
        val details = listOf("Mínimo 8 caracteres", "Debe contener mayúscula")
        val message = authValidator.getErrorMessage(
            AuthValidator.ValidationError.WEAK_PASSWORD,
            details
        )
        
        assertTrue(message.contains("Mínimo 8 caracteres"))
        assertTrue(message.contains("Debe contener mayúscula"))
    }

    @Test
    fun `getErrorMessage returns correct message for PASSWORDS_DO_NOT_MATCH`() {
        val message = authValidator.getErrorMessage(
            AuthValidator.ValidationError.PASSWORDS_DO_NOT_MATCH
        )
        
        assertEquals(AuthStrings.ValidationErrors.PASSWORDS_DO_NOT_MATCH, message)
    }
}
