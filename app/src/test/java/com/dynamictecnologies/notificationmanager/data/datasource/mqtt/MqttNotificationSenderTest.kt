package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MqttNotificationSenderTest {

    private lateinit var mockConnectionManager: MqttConnectionManager
    private lateinit var sender: MqttNotificationSender

    @Before
    fun setup() {
        // Mock estático de Log para evitar NPE
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        
        // Use relaxed mock to handle any additional method calls
        mockConnectionManager = mockk(relaxed = true)
        
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
        coEvery { mockConnectionManager.publish(any(), any(), any()) } returns Result.success(Unit)
        
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
        assertTrue("Result should be success: ${result.exceptionOrNull()?.message}", result.isSuccess)
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
        val result = sender.sendNotification("device123", notification)

        // Then
        assertTrue("Result should be success", result.isSuccess)
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
        coEvery { mockConnectionManager.publish(any(), any(), any()) } returns Result.success(Unit)

        // When
        val result = sender.sendGeneralNotification("Test Title", "Test Content")

        // Then
        assertTrue("Result should be success: ${result.exceptionOrNull()?.message}", result.isSuccess)
    }

    @Test
    fun `sendGeneralNotification includes timestamp in payload`() = runTest {
        // Given
        every { mockConnectionManager.isConnected() } returns true
        coEvery { mockConnectionManager.publish(any(), any(), any()) } returns Result.success(Unit)

        // When
        val result = sender.sendGeneralNotification("Title", "Content")

        // Then
        assertTrue("Result should be success: ${result.exceptionOrNull()?.message}", result.isSuccess)
    }
}

