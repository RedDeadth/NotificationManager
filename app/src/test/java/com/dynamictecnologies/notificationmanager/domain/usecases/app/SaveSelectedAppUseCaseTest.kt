package com.dynamictecnologies.notificationmanager.domain.usecases.app

import com.dynamictecnologies.notificationmanager.domain.repositories.PreferencesRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para SaveSelectedAppUseCase.
 * 
 * Verifica que el Use Case correctamente:
 * - Delega al repository
 * - Guarda el package name correcto
 */
class SaveSelectedAppUseCaseTest {

    private lateinit var mockPreferencesRepository: PreferencesRepository
    private lateinit var useCase: SaveSelectedAppUseCase

    @Before
    fun setup() {
        mockPreferencesRepository = mockk(relaxed = true)
        useCase = SaveSelectedAppUseCase(mockPreferencesRepository)
    }

    @Test
    fun `invoke calls repository with correct package name`() {
        // Given
        val packageName = "com.example.app"
        every { mockPreferencesRepository.saveSelectedApp(any()) } returns Unit

        // When
        useCase(packageName)

        // Then
        verify(exactly = 1) { mockPreferencesRepository.saveSelectedApp(packageName) }
    }

    @Test
    fun `invoke can save empty package name`() {
        // Given
        val emptyPackage = ""
        every { mockPreferencesRepository.saveSelectedApp(any()) } returns Unit

        // When
        useCase(emptyPackage)

        // Then
        verify(exactly = 1) { mockPreferencesRepository.saveSelectedApp(emptyPackage) }
    }

    @Test
    fun `invoke delegates to repository exactly once`() {
        // Given
        val packageName = "com.test.app"
        every { mockPreferencesRepository.saveSelectedApp(any()) } returns Unit

        // When
        useCase(packageName)

        // Then
        verify(exactly = 1) { mockPreferencesRepository.saveSelectedApp(any()) }
    }

    @Test
    fun `invoke works with different package names`() {
        // Given
        val packageNames = listOf(
            "com.app1",
            "com.app2",
            "org.example.test"
        )
        every { mockPreferencesRepository.saveSelectedApp(any()) } returns Unit

        // When/Then
        packageNames.forEach { packageName ->
            useCase(packageName)
            verify { mockPreferencesRepository.saveSelectedApp(packageName) }
        }
    }
}
