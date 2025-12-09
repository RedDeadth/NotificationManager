package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Tests unitarios para MqttNotificationSender.
 * 
 * Verifica:
 * - Envío de notificaciones a dispositivos
 * - Envío de notificaciones generales
 * - Construcción de payloads JSON
 * - Gestión de usuario actual
 */
class MqttNotificationSenderTest {

    private lateinit var mockConnectionManager: MqttConnectionManager
    private lateinit var sender: MqttNotificationSender

    @Before
    fun setup() {
        // NOT relaxed - need to configure each mock explicitly for Result types
        mockConnectionManager = mockk()
        
        // Default: isConnected returns false (safe default)
        every { mockConnectionManager.isConnected() } returns false
        
        sender = MqttNotificationSender(mockConnectionManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `setCurrentUser stores user information`() {
        // Given
        val userId = "user123"
        val username = "testuser"

        // When
        sender.setCurrentUser(userId, username)

        // Then - no exception should be thrown
        // User info should be stored internally
    }

    @Test
    fun `sendNotification returns failure when not connected`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns false
        val notification = NotificationInfo(
            id = 1L,
            title = "Test",
            content = "Content",
            appName = "App",
            timestamp = Date()
        )

        // When
        val result = sender.sendNotification("device123", notification)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        assertTrue(
            "Error should mention not connected",
            result.exceptionOrNull()?.message?.contains("MQTT no conectado") == true
        )
    }

    @Test
    fun `sendNotification publishes to correct topic when connected`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns true
        coEvery { mockConnectionManager.publish(any(), any(), any()) } coAnswers { Result.success(Unit) }
        
        val deviceId = "device123"
        val notification = NotificationInfo(
            id = 1L,
            title = "Test Notification",
            content = "Test Content",
            appName = "WhatsApp",
            timestamp = Date(1234567890000L)
        )

        // When
        val result = sender.sendNotification(deviceId, notification)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        coVerify {
            mockConnectionManager.publish(
                eq("esp32/device/$deviceId/notification"),
                any(), // payload contains JSON
                eq(1)
            )
        }
    }

    @Test
    fun `sendNotification includes user info in payload when set`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns true
        var capturedPayload = ""
        coEvery { mockConnectionManager.publish(any(), any(), any()) } coAnswers {
            capturedPayload = secondArg()
            Result.success(Unit)
        }
        
        sender.setCurrentUser("user123", "testuser")
        val notification = NotificationInfo(
            id = 1L,
            title = "Test",
            content = "Content",
            appName = "App",
            timestamp = Date()
        )

        // When
        sender.sendNotification("device123", notification)

        // Then
        assertTrue("Payload should contain userId", capturedPayload.contains("user123"))
        assertTrue("Payload should contain username", capturedPayload.contains("testuser"))
    }

    @Test
    fun `sendGeneralNotification returns failure when not connected`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns false

        // When
        val result = sender.sendGeneralNotification("Title", "Content")

        // Then
        assertTrue("Result should be failure", result.isFailure)
    }

    @Test
    fun `sendGeneralNotification publishes to general topic when connected`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns true
        coEvery { mockConnectionManager.publish(any(), any(), any()) } coAnswers { Result.success(Unit) }

        // When
        val result = sender.sendGeneralNotification("Test Title", "Test Content")

        // Then
        assertTrue("Result should be success", result.isSuccess)
        coVerify {
            mockConnectionManager.publish(
                eq("/notificaciones/general"),
                any(),
                any()
            )
        }
    }

    @Test
    fun `sendGeneralNotification includes timestamp in payload`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns true
        var capturedPayload = ""
        coEvery { mockConnectionManager.publish(any(), any(), any()) } coAnswers {
            capturedPayload = secondArg()
            Result.success(Unit)
        }

        // When
        sender.sendGeneralNotification("Title", "Content")

        // Then
        assertTrue("Payload should contain timestamp", capturedPayload.contains("timestamp"))
        assertTrue("Payload should contain title", capturedPayload.contains("Title"))
        assertTrue("Payload should contain content", capturedPayload.contains("Content"))
    }
}
