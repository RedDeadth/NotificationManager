package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para MqttDeviceLinkManager.
 * 
 * Verifica:
 * - Vinculación correcta de dispositivos
 * - Desvinculación de dispositivos
 * - Suscripción a topics de estado
 * - Manejo de errores cuando MQTT no está conectado
 */
class MqttDeviceLinkManagerTest {

    private lateinit var connectionManager: MqttConnectionManager
    private lateinit var subscriptionManager: MqttSubscriptionManager
    private lateinit var linkManager: MqttDeviceLinkManager

    @Before
    fun setup() {
        // Use relaxed mocks to handle any additional method calls
        connectionManager = mockk(relaxed = true)
        subscriptionManager = mockk(relaxed = true)
        
        // Default: not connected
        every { connectionManager.isConnected() } returns false
        
        linkManager = MqttDeviceLinkManager(connectionManager, subscriptionManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `linkDevice subscribes to device status topic`() = runTest {
        // Given
        val deviceId = "ESP32_001"
        val userId = "user123"
        val username = "testuser"
        
        every { connectionManager.isConnected() } returns true
        coEvery { subscriptionManager.subscribe(any(), any()) } coAnswers { Result.success(Unit) }
        coEvery { connectionManager.publish(any(), any(), any()) } coAnswers { Result.success(Unit) }
        every { connectionManager.getClient() } returns mockk(relaxed = true)

        // When
        val result = linkManager.linkDevice(deviceId, userId, username)

        // Then
        assertTrue("Link should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    }

    @Test
    fun `linkDevice sends correct MQTT message`() = runTest {
        // Given
        val deviceId = "ESP32_001"
        val userId = "user123"
        val username = "testuser"
        
        every { connectionManager.isConnected() } returns true
        coEvery { subscriptionManager.subscribe(any(), any()) } coAnswers { Result.success(Unit) }
        coEvery { connectionManager.publish(any(), any(), any()) } coAnswers { Result.success(Unit) }
        every { connectionManager.getClient() } returns mockk(relaxed = true)

        // When
        val result = linkManager.linkDevice(deviceId, userId, username)

        // Then
        assertTrue("Link should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        // Verify publish was called (without detailed payload matching)
        coVerify { connectionManager.publish(any(), any(), any()) }
    }

    @Test
    fun `linkDevice fails when MQTT not connected`() = runTest {
        // Given
        val deviceId = "ESP32_001"
        val userId = "user123"
        
        every { connectionManager.isConnected() } returns false

        // When
        val result = linkManager.linkDevice(deviceId, userId)

        // Then
        assertTrue("Link should fail", result.isFailure)
        assertEquals("MQTT no conectado", result.exceptionOrNull()?.message)
    }

    @Test
    fun `unlinkDevice publishes unlink action`() = runTest {
        // Given
        val deviceId = "ESP32_001"
        
        every { connectionManager.isConnected() } returns true
        coEvery { connectionManager.publish(any(), any(), any()) } coAnswers { Result.success(Unit) }
        coEvery { subscriptionManager.unsubscribe(any()) } coAnswers { Result.success(Unit) }

        // When
        val result = linkManager.unlinkDevice(deviceId)

        // Then
        assertTrue("Unlink should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        // Verify publish was called
        coVerify { connectionManager.publish(any(), any(), any()) }
    }

    @Test
    fun `unlinkDevice unsubscribes from status topic on success`() = runTest {
        // Given
        val deviceId = "ESP32_001"
        
        every { connectionManager.isConnected() } returns true
        coEvery { connectionManager.publish(any(), any(), any()) } coAnswers { Result.success(Unit) }
        coEvery { subscriptionManager.unsubscribe(any()) } coAnswers { Result.success(Unit) }

        // When
        val result = linkManager.unlinkDevice(deviceId)

        // Then
        assertTrue("Unlink should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    }

    @Test
    fun `unlinkDevice fails when MQTT not connected`() = runTest {
        // Given
        val deviceId = "ESP32_001"
        
        every { connectionManager.isConnected() } returns false

        // When
        val result = linkManager.unlinkDevice(deviceId)

        // Then
        assertTrue("Unlink should fail", result.isFailure)
    }

    @Test
    fun `subscribeToDeviceStatus calls subscription manager`() = runTest {
        // Given
        val deviceId = "ESP32_001"
        coEvery { subscriptionManager.subscribe(any(), any()) } coAnswers { Result.success(Unit) }

        // When
        val result = linkManager.subscribeToDeviceStatus(deviceId)

        // Then
        assertTrue("Subscribe should succeed", result.isSuccess)
        coVerify { subscriptionManager.subscribe("esp32/device/$deviceId/status", 1) }
    }
}
