package com.dynamictecnologies.notificationmanager.domain.usecases.app

import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.AppRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.PreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para GetSelectedAppUseCase.
 * 
 * Verifica que el Use Case correctamente:
 * - Coordina PreferencesRepository y AppRepository
 * - Retorna null cuando no hay app seleccionada
 * - Retorna app cuando existe
 * - Maneja caso donde app fue desinstalada
 */
class GetSelectedAppUseCaseTest {

    private lateinit var mockPreferencesRepository: PreferencesRepository
    private lateinit var mockAppRepository: AppRepository
    private lateinit var useCase: GetSelectedAppUseCase

    @Before
    fun setup() {
        mockPreferencesRepository = mockk()
        mockAppRepository = mockk()
        useCase = GetSelectedAppUseCase(mockPreferencesRepository, mockAppRepository)
    }

    @Test
    fun `invoke returns null when no app is selected`() = runTest {
        // Given
        every { mockPreferencesRepository.getSelectedApp() } returns null

        // When
        val result = useCase()

        // Then
        assertNull("Should return null when no app selected", result)
        verify(exactly = 1) { mockPreferencesRepository.getSelectedApp() }
        coVerify(exactly = 0) { mockAppRepository.getAppByPackageName(any()) }
    }

    @Test
    fun `invoke returns app when selected app exists`() = runTest {
        // Given
        val packageName = "com.example.app"
        val expectedApp = AppInfo("Example App", packageName, null)
        every { mockPreferencesRepository.getSelectedApp() } returns packageName
        coEvery { mockAppRepository.getAppByPackageName(packageName) } returns Result.success(expectedApp)

        // When
        val result = useCase()

        // Then
        assertNotNull("Should return app", result)
        assertEquals("Should return correct app", expectedApp, result)
        verify(exactly = 1) { mockPreferencesRepository.getSelectedApp() }
        coVerify(exactly = 1) { mockAppRepository.getAppByPackageName(packageName) }
    }

    @Test
    fun `invoke returns null when selected app was uninstalled`() = runTest {
        // Given
        val packageName = "com.uninstalled.app"
        every { mockPreferencesRepository.getSelectedApp() } returns packageName
        coEvery { mockAppRepository.getAppByPackageName(packageName) } returns 
            Result.failure(Exception("App not found"))

        // When
        val result = useCase()

        // Then
        assertNull("Should return null when app not found", result)
        verify(exactly = 1) { mockPreferencesRepository.getSelectedApp() }
        coVerify(exactly = 1) { mockAppRepository.getAppByPackageName(packageName) }
    }

    @Test
    fun `invoke calls repositories in correct order`() = runTest {
        // Given
        val packageName = "com.test.app"
        val app = AppInfo("Test App", packageName, null)
        val callOrder = mutableListOf<String>()
        
        every { mockPreferencesRepository.getSelectedApp() } answers {
            callOrder.add("preferences")
            packageName
        }
        coEvery { mockAppRepository.getAppByPackageName(packageName) } coAnswers {
            callOrder.add("appRepository")
            Result.success(app)
        }

        // When
        useCase()

        // Then
        assertEquals("Should call preferences first", "preferences", callOrder[0])
        assertEquals("Should call app repository second", "appRepository", callOrder[1])
    }

    @Test
    fun `invoke works with different package names`() = runTest {
        // Given
        val testCases = listOf(
            "com.app1" to AppInfo("App 1", "com.app1", null),
            "com.app2" to AppInfo("App 2", "com.app2", null),
            "org.test" to AppInfo("Test", "org.test", null)
        )

        testCases.forEach { (packageName, expectedApp) ->
            // Given
            every { mockPreferencesRepository.getSelectedApp() } returns packageName
            coEvery { mockAppRepository.getAppByPackageName(packageName) } returns Result.success(expectedApp)

            // When
            val result = useCase()

            // Then
            assertEquals("Should return correct app for $packageName", expectedApp, result)
        }
    }
}
