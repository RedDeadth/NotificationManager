package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthErrorCode
import com.dynamictecnologies.notificationmanager.data.exceptions.AuthException
import com.dynamictecnologies.notificationmanager.data.mapper.AuthErrorMapper
import com.dynamictecnologies.notificationmanager.data.validator.AuthValidator
import com.dynamictecnologies.notificationmanager.data.validator.UsernameValidator
import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.dynamictecnologies.notificationmanager.domain.entities.UserProfile
import com.dynamictecnologies.notificationmanager.domain.usecases.GetCurrentUserUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.RegisterUserWithUsernameUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithGoogleUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignOutUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.ValidateSessionUseCase
import com.dynamictecnologies.notificationmanager.presentation.auth.GoogleSignInHelper
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests para AuthViewModel
 * 
 * Principios aplicados:
 * - SOLID: Cada test verifica un comportamiento específico (SRP)
 * - Clean Code: AAA pattern, nombres descriptivos
 * - DRY: Helpers para setup y mocks comunes
 * - Testability: Uso de Turbine para StateFlows, MockK para dependencies
 * 
 * Arquitectura de tests:
 * - Given-When-Then pattern
 * - Test doubles con MockK (más flexible que Mockito para Kotlin)
 * - Coroutines testing con StandardTestDispatcher
 * - Turbine para testing reactivo de Flows
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    // ===== TEST DEPENDENCIES (Following Dependency Inversion Principle) =====
    
    private lateinit var viewModel: AuthViewModel
    private lateinit var authRepository: com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
    private lateinit var signInWithEmailUseCase: SignInWithEmailUseCase
    private lateinit var registerUserWithUsernameUseCase: RegisterUserWithUsernameUseCase
    private lateinit var signInWithGoogleUseCase: SignInWithGoogleUseCase
    private lateinit var signOutUseCase: SignOutUseCase
    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var validateSessionUseCase: ValidateSessionUseCase
    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var errorMapper: AuthErrorMapper
    private lateinit var authValidator: AuthValidator
    private lateinit var usernameValidator: UsernameValidator

    // FIXED: Usar UnconfinedTestDispatcher para ejecución inmediata
    private val testDispatcher = UnconfinedTestDispatcher()

    // ===== TEST FIXTURES (DRY Principle - reuse across tests) =====
    
    private val mockUser = User(
        id = "test-uid",
        username = "testuser",
        email = "test@example.com"
    )

    private val mockUserProfile = UserProfile(
        uid = "test-uid",
        username = "testuser",
        email = "test@example.com",
        createdAt = System.currentTimeMillis(),
        isActive = true
    )

    // ===== SETUP & TEARDOWN =====
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Initialize mocks (Following DIP - depend on abstractions)
        authRepository = mockk(relaxed = true)
        signInWithEmailUseCase = mockk(relaxed = true)
        registerUserWithUsernameUseCase = mockk(relaxed = true)
        signInWithGoogleUseCase = mockk(relaxed = true)
        signOutUseCase = mockk(relaxed = true)
        getCurrentUserUseCase = mockk(relaxed = true)
        validateSessionUseCase = mockk(relaxed = true)
        googleSignInHelper = mockk(relaxed = true)
        errorMapper = mockk(relaxed = true)
        authValidator = mockk(relaxed = true)
        usernameValidator = mockk(relaxed = true)

        // Default mock behaviors (happy path)
        coEvery { validateSessionUseCase() } returns true
        coEvery { getCurrentUserUseCase() } returns flowOf(null)
        every { errorMapper.mapException(any()) } returns AuthException(
            code = AuthErrorCode.UNKNOWN_ERROR,
            message = "Error desconocido"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ===== HELPER METHODS (DRY Principle) =====
    
    private fun createViewModel() {
        viewModel = AuthViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = authRepository,
            signInWithEmailUseCase = signInWithEmailUseCase,
            registerUserWithUsernameUseCase = registerUserWithUsernameUseCase,
            signInWithGoogleUseCase = signInWithGoogleUseCase,
            signOutUseCase = signOutUseCase,
            getCurrentUserUseCase = getCurrentUserUseCase,
            validateSessionUseCase = validateSessionUseCase,
            googleSignInHelper = googleSignInHelper,
            errorMapper = errorMapper,
            authValidator = authValidator,
            usernameValidator = usernameValidator
        )
    }

    // ===== AUTH STATE INITIALIZATION TESTS =====

    @Test
    fun `checkAuthState - usuario no autenticado inicialmente`() = runTest {
        // Given: No hay usuario en sesión
        coEvery { getCurrentUserUseCase() } returns flowOf(null)
        coEvery { validateSessionUseCase() } returns false

        // When: Se crea el ViewModel
        createViewModel()

        // Then: Estado debe reflejar no autenticado
        viewModel.authState.test {
            val state = awaitItem()
            assertFalse("Usuario no debe estar autenticado", state.isAuthenticated)
            assertNull("currentUser debe ser null", state.currentUser)
            assertFalse("Sesión no debe ser válida", state.isSessionValid)
            assertFalse("No debe estar cargando", state.isLoading)
        }
    }

    @Test
    fun `checkAuthState - usuario autenticado con sesión válida`() = runTest {
        // Given: Hay usuario en sesión
        coEvery { getCurrentUserUseCase() } returns flowOf(mockUser)
        coEvery { validateSessionUseCase() } returns true

        // When: Se crea el ViewModel
        createViewModel()

        // Then: Estado debe reflejar autenticado
        viewModel.authState.test {
            val state = awaitItem()
            assertTrue("Usuario debe estar autenticado", state.isAuthenticated)
            assertEquals("currentUser debe coincidir", mockUser, state.currentUser)
            assertTrue("Sesión debe ser válida", state.isSessionValid)
        }
    }

    // ===== SIGN IN WITH EMAIL TESTS =====

    @Test
    fun `signInWithEmail - éxito actualiza estado de autenticación`() = runTest {
        // Given: Use case retorna éxito
        coEvery { signInWithEmailUseCase(any(), any()) } returns Result.success(mockUser)
        coEvery { getCurrentUserUseCase() } returns flowOf(mockUser)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Usuario inicia sesión
        viewModel.signInWithEmail("test@example.com", "password123")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final directamente
        val finalState = viewModel.authState.value
        assertTrue("Debe estar autenticado", finalState.isAuthenticated)
        assertEquals("User debe coincidir", mockUser, finalState.currentUser)
        assertFalse("No debe estar loading", finalState.isLoading)
        assertNull("No debe haber error", finalState.error)
    }

    @Test
    fun `signInWithEmail - credenciales inválidas muestra error`() = runTest {
        // Given: Use case retorna failure
        val exception = FirebaseAuthInvalidCredentialsException("auth/invalid-credential", "Invalid")
        val authEx = AuthException(
            code = AuthErrorCode.INVALID_CREDENTIALS,
            message = "Credenciales inválidas",
            cause = exception
        )
        
        coEvery { signInWithEmailUseCase(any(), any()) } returns Result.failure(exception)
        every { errorMapper.mapException(exception) } returns authEx
        every { errorMapper.getLocalizedErrorMessage(authEx) } returns "Credenciales inválidas"
        
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Usuario intenta login con credenciales malas
        viewModel.signInWithEmail("test@example.com", "wrongpass")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final con error
        val finalState = viewModel.authState.value
        assertFalse("No debe estar autenticado", finalState.isAuthenticated)
        assertNotNull("Debe haber error", finalState.error)
        assertFalse("No debe estar loading", finalState.isLoading)
    }

    @Test
    fun `signInWithEmail - error de red muestra mensaje apropiado`() = runTest {
        // Given: Falla de red
        val exception = Exception("Network error")
        val authEx = AuthException(
            code = AuthErrorCode.NETWORK_ERROR,
            message = "Error de red",
            cause = exception
        )
        
        coEvery { signInWithEmailUseCase(any(), any()) } returns Result.failure(exception)
        every { errorMapper.mapException(exception) } returns authEx
        every { errorMapper.getLocalizedErrorMessage(authEx) } returns "Error de red"
        
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Sign in falla por red
        viewModel.signInWithEmail("test@example.com", "password123")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final con error
        val finalState = viewModel.authState.value
        assertNotNull("Debe mostrar error", finalState.error)
        assertFalse("No debe estar loading", finalState.isLoading)
    }

    // ===== REGISTER  WITH EMAIL TESTS =====

    @Test
    fun `registerWithEmail - éxito crea usuario`() = runTest {
        // Given: Register use case exitoso
        coEvery { registerUserWithUsernameUseCase(any(), any(), any()) } returns Result.success(mockUserProfile)
        coEvery { validateSessionUseCase() } returns true
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Usuario se registra
        viewModel.registerWithEmail("test@example.com", "pass123", "pass123", "testuser")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final
        val finalState = viewModel.authState.value
        assertTrue("Debe estar autenticado", finalState.isAuthenticated)
        assertEquals("Username debe coincidir", "testuser", finalState.currentUser?.username)
        assertNull("No debe haber error", finalState.error)

        // Verify use case was called correctly
        coVerify { registerUserWithUsernameUseCase("test@example.com", "pass123", "testuser") }
    }

    @Test
    fun `registerWithEmail - usuario existente muestra error`() = runTest {
        // Given: Email ya registrado
        val exception = FirebaseAuthUserCollisionException("auth/email-in-use", "Email exists")
        val authEx = AuthException(
            code = AuthErrorCode.EMAIL_ALREADY_IN_USE,
            message = "El correo ya está en uso",
            cause = exception
        )
        
        coEvery { registerUserWithUsernameUseCase(any(), any(), any()) } returns Result.failure(exception)
        every { errorMapper.mapException(exception) } returns authEx
        every { errorMapper.getLocalizedErrorMessage(authEx) } returns "El correo ya está en uso"
        
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Intento de registro con email existente
        viewModel.registerWithEmail("existing@example.com", "pass123", "pass123", "testuser")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final con error
        val finalState = viewModel.authState.value
        assertFalse("No debe estar autenticado", finalState.isAuthenticated)
        assertNotNull("Debe mostrar error", finalState.error)
        assertFalse("No debe estar loading", finalState.isLoading)
    }

    // ===== GOOGLE SIGN IN TESTS =====

    @Test
    fun `getGoogleSignInIntent - retorna intent del helper`() {
        // Given: Helper configurado
        val mockIntent = mockk<Intent>()
        every { googleSignInHelper.getSignInIntent() } returns mockIntent
        createViewModel()

        // When: Solicitar intent
        val intent = viewModel.getGoogleSignInIntent()

        // Then: Debe retornar el intent correcto
        assertEquals("Intent debe coincidir", mockIntent, intent)
        coVerify { googleSignInHelper.getSignInIntent() }
    }

    @Test
    fun `handleGoogleSignInResult - éxito autentica usuario`() = runTest {
        // Given: Google sign in exitoso
        val mockIntent = mockk<Intent>()
        val idToken = "mock-id-token"
        every { googleSignInHelper.getIdTokenFromIntent(mockIntent) } returns idToken
        coEvery { signInWithGoogleUseCase(idToken) } returns Result.success(mockUser)
        coEvery { getCurrentUserUseCase() } returns flowOf(mockUser)
        
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Handle Google result
        viewModel.handleGoogleSignInResult(mockIntent)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final
        val finalState = viewModel.authState.value
        assertTrue("Debe estar autenticado", finalState.isAuthenticated)
        assertEquals("Usuario debe coincidir", mockUser, finalState.currentUser)
    }

    @Test
    fun `handleGoogleSignInResult - resultado nulo muestra error`() = runTest {
        // Given: ViewModel inicializado
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Result es null (usuario canceló)
        viewModel.handleGoogleSignInResult(null)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final
        val finalState = viewModel.authState.value
        assertNotNull("Debe haber mensaje de error", finalState.error)
    }

    // ===== SIGN OUT TESTS =====

    @Test
    fun `signOut - limpia estado de autenticación`() = runTest {
        // Given: Usuario autenticado
        coEvery { signOutUseCase() } returns Result.success(Unit)
        coEvery { getCurrentUserUseCase() } returns flowOf(mockUser) andThen flowOf(null)
        
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Usuario cierra sesión
        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final
        val finalState = viewModel.authState.value
        assertFalse("No debe estar autenticado", finalState.isAuthenticated)
        assertNull("currentUser debe ser null", finalState.currentUser)

        coVerify { signOutUseCase() }
    }

    @Test
    fun `signOut - maneja errores gracefully`() = runTest {
        // Given: Sign out puede fallar
        val exception = Exception("Sign out failed")
        val authEx = AuthException(
            code = AuthErrorCode.UNKNOWN_ERROR,
            message = "Error al cerrar sesión",
            cause = exception
        )
        
        coEvery { signOutUseCase() } returns Result.failure(exception)
        every { errorMapper.mapException(exception) } returns authEx
        every { errorMapper.getLocalizedErrorMessage(authEx) } returns "Error al cerrar sesión"
        
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Sign out falla
        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final con error
        val finalState = viewModel.authState.value
        assertNotNull("Debe mostrar error", finalState.error)
        assertFalse("No debe estar loading", finalState.isLoading)
    }

    // ===== FORM VALIDATION TESTS =====

    @Test
    fun `updateLoginEmail - valida formato de email correctamente`() = runTest {
        // Given: Validator configurado
        every { authValidator.validateEmail(any()) } returns AuthValidator.ValidationResult.Valid
        createViewModel()

        // When: Actualizar email
        viewModel.loginFormState.test {
            skipItems(1) // Skip inicial
            viewModel.updateLoginEmail("valid@example.com")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Email actualizado, sin error
            val state = awaitItem()
            assertEquals("Email debe actualizarse", "valid@example.com", state.email)
            assertNull("No debe haber error", state.emailError)
        }
    }

    @Test
    fun `updateLoginEmail - muestra error para email inválido`() = runTest {
        // Given: Validator rechaza email
        every { authValidator.validateEmail(any()) } returns 
            AuthValidator.ValidationResult.Invalid(AuthValidator.ValidationError.INVALID_EMAIL_FORMAT)
        every { authValidator.getErrorMessage(any(), any()) } returns "Formato inválido"
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Email inválido
        viewModel.updateLoginEmail("invalid-email")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Verificar estado final
        val state = viewModel.loginFormState.value
        assertEquals("Email debe actualizarse", "invalid-email", state.email)
        assertNotNull("Debe haber error de email", state.emailError)
    }

    @Test
    fun `clearError - remueve error del estado`() = runTest {
        // Given: ViewModel con error
        val exception = Exception("Test error")
        val authEx = AuthException(
            code = AuthErrorCode.UNKNOWN_ERROR,
            message = "Test error",
            cause = exception
        )
        
        coEvery { signInWithEmailUseCase(any(), any()) } returns Result.failure(exception)
        every { errorMapper.mapException(exception) } returns authEx
        
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.signInWithEmail("test@example.com", "password")
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Clear error
        viewModel.clearError()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Error limpio
        viewModel.authState.test {
            val state = awaitItem()
            assertNull("Error debe estar limpio", state.error)
        }
    }

    // ===== REGISTER FORM VALIDATION =====

    @Test
    fun `updateRegisterUsername - válida username correctamente`() = runTest {
        // Given: Username validator configurado
        every { usernameValidator.validate(any()) } returns UsernameValidator.ValidationResult.Valid
        createViewModel()

        // When: Username válido
        viewModel.registerFormState.test {
            skipItems(1)
            viewModel.updateRegisterUsername("validuser")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Sin error
            val state = awaitItem()
            assertEquals("Username debe actualizarse", "validuser", state.username)
            assertNull("No debe haber error", state.usernameError)
        }
    }

    @Test
    fun `updateRegisterPassword - valida fortaleza de contraseña`() = runTest {
        // Given: Password validator configurado
        every { authValidator.validatePassword(any()) } returns AuthValidator.ValidationResult.Valid
        createViewModel()

        // When: Password fuerte
        viewModel.registerFormState.test {
            skipItems(1)
            viewModel.updateRegisterPassword("StrongP@ss123")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Sin error
            val state = awaitItem()
            assertEquals("Password debe actualizarse", "StrongP@ss123", state.password)
            assertNull("No debe haber error", state.passwordError)
        }
    }

    @Test
    fun `clearLoginForm - resetea estado del formulario`() = runTest {
        // Given: Formulario con datos
        createViewModel()
        viewModel.updateLoginEmail("test@example.com")
        viewModel.updateLoginPassword("password123")
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Clear form
        viewModel.clearLoginForm()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Formulario limpio
        viewModel.loginFormState.test {
            val state = awaitItem()
            assertEquals("Email debe estar vacío", "", state.email)
            assertEquals("Password debe estar vacío", "", state.password)
        }
    }

    @Test
    fun `clearRegisterForm - resetea estado de registro`() = runTest {
        // Given: Formulario de registro con datos
        createViewModel()
        viewModel.updateRegisterEmail("test@example.com")
        viewModel.updateRegisterPassword("password123")
        viewModel.updateRegisterUsername("testuser")
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Clear
        viewModel.clearRegisterForm()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Todo limpio
        viewModel.registerFormState.test {
            val state = awaitItem()
            assertEquals("Email vacío", "", state.email)
            assertEquals("Password vacío", "", state.password)
            assertEquals("Username vacío", "", state.username)
        }
    }
}
