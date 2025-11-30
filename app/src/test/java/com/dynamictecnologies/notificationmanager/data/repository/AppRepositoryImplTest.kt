package com.dynamictecnologies.notificationmanager.data.repository

import com.dynamictecnologies.notificationmanager.data.datasource.AppDataSource
import com.dynamictecnologies.notificationmanager.data.mapper.AppInfoMapper
import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.data.model.AppInfoData
import com.dynamictecnologies.notificationmanager.domain.repositories.AppRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para AppRepositoryImpl.
 * 
 * Verifica que el repository correctamente:
 * - Coordina data source y mapper
 * - Transforma AppInfoData a AppInfo
 * - Maneja casos de éxito y error
 * - Retorna Result wrapper
 */
class AppRepositoryImplTest {

    private lateinit var mockAppDataSource: AppDataSource
    private lateinit var repository: AppRepository

    @Before
    fun setup() {
        mockAppDataSource = mockk()
        repository = AppRepositoryImpl(mockAppDataSource)
    }

    @Test
    fun `getInstalledApps returns success with mapped apps`() = runTest {
        // Given
        val appsData = listOf(
            AppInfoData("App1", "com.app1", null),
            AppInfoData("App2", "com.app2", null)
        )
        coEvery { mockAppDataSource.getInstalledApplications() } returns appsData

        // When
        val result = repository.getInstalledApps()

        // Then
        assertTrue("Result should be success", result.isSuccess)
        
        val apps = result.getOrNull()
        assertNotNull("Should return apps", apps)
        assertEquals("Should return correct number of apps", 2, apps?.size)
        assertEquals("Should map app names correctly", "App1", apps?.get(0)?.name)
        assertEquals("Should map package names correctly", "com.app1", apps?.get(0)?.packageName)
        
        coVerify(exactly = 1) { mockAppDataSource.getInstalledApplications() }
    }

    @Test
    fun `getInstalledApps returns empty list when no apps installed`() = runTest {
        // Given
        coEvery { mockAppDataSource.getInstalledApplications() } returns emptyList()

        // When
        val result = repository.getInstalledApps()

        // Then
        assertTrue("Result should be success", result.isSuccess)
        assertTrue("Should return empty list", result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `getInstalledApps returns failure when data source throws exception`() = runTest {
        // Given
        val expectedException = Exception("Data source error")
        coEvery { mockAppDataSource.getInstalledApplications() } throws expectedException

        // When
        val result = repository.getInstalledApps()

        // Then
        assertTrue("Result should be failure", result.isFailure)
        assertNotNull("Should contain exception", result.exceptionOrNull())
    }

    @Test
    fun `getAppByPackageName returns success with mapped app`() = runTest {
        // Given
        val packageName = "com.example.app"
        val appData = AppInfoData("Example App", packageName, null)
        coEvery { mockAppDataSource.getApplicationInfo(packageName) } returns appData

        // When
        val result = repository.getAppByPackageName(packageName)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        
        val app = result.getOrNull()
        assertNotNull("Should return app", app)
        assertEquals("Should map name correctly", "Example App", app?.name)
        assertEquals("Should map package correctly", packageName, app?.packageName)
        
        coVerify(exactly = 1) { mockAppDataSource.getApplicationInfo(packageName) }
    }

    @Test
    fun `getAppByPackageName returns failure when app not found`() = runTest {
        // Given
        val packageName = "com.nonexistent.app"
        coEvery { mockAppDataSource.getApplicationInfo(packageName) } returns null

        // When
        val result = repository.getAppByPackageName(packageName)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        assertTrue(
            "Error message should indicate app not found",
            result.exceptionOrNull()?.message?.contains("no encontrada") == true
        )
    }

    @Test
    fun `getAppByPackageName returns failure when data source throws exception`() = runTest {
        // Given
        val packageName = "com.error.app"
        val expectedException = Exception("Data source error")
        coEvery { mockAppDataSource.getApplicationInfo(packageName) } throws expectedException

        // When
        val result = repository.getAppByPackageName(packageName)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        assertNotNull("Should contain exception", result.exceptionOrNull())
    }

    @Test
    fun `getInstalledApps correctly maps multiple apps`() = runTest {
        // Given
        val appsData = listOf(
            AppInfoData("App A", "com.a", null),
            AppInfoData("App B", "com.b", null),
            AppInfoData("App C", "com.c", null)
        )
        coEvery { mockAppDataSource.getInstalledApplications() } returns appsData

        // When
        val result = repository.getInstalledApps()

        // Then
        val apps = result.getOrNull()
        assertEquals("Should map all apps", 3, apps?.size)
        
        apps?.forEachIndexed { index, app ->
            assertEquals("Should preserve app data", appsData[index].name, app.name)
            assertEquals("Should preserve package", appsData[index].packageName, app.packageName)
        }
    }
}
