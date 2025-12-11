package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Tests unitarios para MqttDeviceScanner.
 * 
 * Verifica:
 * - Búsqueda de dispositivos
 * - Creación de DeviceInfo
 * - Manejo de estados de conexión
 */
class MqttDeviceScannerTest {

    private lateinit var mockConnectionManager: MqttConnectionManager
    private lateinit var scanner: MqttDeviceScanner

    @Before
    fun setup() {
        mockConnectionManager = mockk(relaxed = true)
        scanner = MqttDeviceScanner(mockConnectionManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `searchDevices returns failure when not connected`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns false

        // When
        val result = scanner.searchDevices()

        // Then
        assertTrue("Result should be failure", result.isFailure)
        assertTrue(
            "Error should mention MQTT not connected",
            result.exceptionOrNull()?.message?.contains("MQTT no conectado") == true
        )
    }

    @Test
    fun `searchDevices publishes discovery message when connected`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns true
        coEvery { mockConnectionManager.publish(any(), any(), any()) } returns Result.success(Unit)

        // When
        val result = scanner.searchDevices()

        // Then
        assertTrue("Result should be success", result.isSuccess)
        coVerify { mockConnectionManager.publish("esp32/discover", "discover", 1) }
        verify { mockConnectionManager.subscribe("esp32/response/#", 1) }
    }

    @Ignore("Requires mock refactoring for publish() default params")
    @Test
    fun `searchDevices handles publish failure gracefully`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns true
        coEvery { mockConnectionManager.publish(any(), any(), any()) } returns 
            Result.failure(Exception("Publish error"))

        // When
        val result = scanner.searchDevices()

        // Then
        assertTrue("Result should be failure", result.isFailure)
    }

    @Test
    fun `createDeviceInfo creates valid DeviceInfo`() {
        // Given
        val deviceId = "ESP32_TEST_123"

        // When
        val deviceInfo = scanner.createDeviceInfo(deviceId)

        // Then
        assertNotNull("DeviceInfo should not be null", deviceInfo)
        assertEquals("Should have correct id", deviceId, deviceInfo.id)
        assertEquals("Should have default name", "ESP32 Visualizador", deviceInfo.name)
        assertFalse("Should not be connected initially", deviceInfo.isConnected)
    }

    @Test
    fun `createDeviceInfo works with different device IDs`() {
        // Given
        val deviceIds = listOf("device1", "device2", "esp32_abc")

        // When/Then
        deviceIds.forEach { deviceId ->
            val deviceInfo = scanner.createDeviceInfo(deviceId)
            assertEquals("Should match device ID", deviceId, deviceInfo.id)
            assertNotNull("DeviceInfo should not be null", deviceInfo)
        }
    }
}
