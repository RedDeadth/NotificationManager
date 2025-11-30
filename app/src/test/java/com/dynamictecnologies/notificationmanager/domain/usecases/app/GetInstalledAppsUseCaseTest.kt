package com.dynamictecnologies.notificationmanager.domain.usecases.app

import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.AppRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para GetInstalledAppsUseCase.
 * 
 * Verifica que el Use Case correctamente:
 * - Delega al repository
 * - Retorna el Result correctamente
 * - Maneja casos de éxito y error
 */
class GetInstalledAppsUseCaseTest {

    private lateinit var mockAppRepository: AppRepository
    private lateinit var useCase: GetInstalledAppsUseCase

    @Before
    fun setup() {
        mockAppRepository = mockk()
        useCase = GetInstalledAppsUseCase(mockAppRepository)
    }

    @Test
    fun `invoke returns success with apps list when repository succeeds`() = runTest {
        // Given
        val expectedApps = listOf(
            AppInfo("App1", "com.app1", null),
            AppInfo("App2", "com.app2", null),
            AppInfo("App3", "com.app3", null)
        )
        coEvery { mockAppRepository.getInstalledApps() } returns Result.success(expectedApps)

        // When
        val result = useCase()

        // Then
        assertTrue("Result should be success", result.isSuccess)
        assertEquals("Should return expected apps", expectedApps, result.getOrNull())
        coVerify(exactly = 1) { mockAppRepository.getInstalledApps() }
    }

    @Test
    fun `invoke returns empty list when repository returns empty`() = runTest {
        // Given
        coEvery { mockAppRepository.getInstalledApps() } returns Result.success(emptyList())

        // When
        val result = useCase()

        // Then
        assertTrue("Result should be success", result.isSuccess)
        assertTrue("Should return empty list", result.getOrNull()?.isEmpty() == true)
        coVerify(exactly = 1) { mockAppRepository.getInstalledApps() }
    }

    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        // Given
        val expectedException = Exception("Repository error")
        coEvery { mockAppRepository.getInstalledApps() } returns Result.failure(expectedException)

        // When
        val result = useCase()

        // Then
        assertTrue("Result should be failure", result.isFailure)
        assertEquals("Should return same exception", expectedException, result.exceptionOrNull())
        coVerify(exactly = 1) { mockAppRepository.getInstalledApps() }
    }

    @Test
    fun `invoke delegates to repository exactly once`() = runTest {
        // Given
        coEvery { mockAppRepository.getInstalledApps() } returns Result.success(emptyList())

        // When
        useCase()

        // Then
        coVerify(exactly = 1) { mockAppRepository.getInstalledApps() }
    }
}
