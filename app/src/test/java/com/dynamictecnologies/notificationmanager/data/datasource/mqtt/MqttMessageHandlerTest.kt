package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import android.content.Context
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Tests unitarios para MqttMessageHandler.
 * 
 * Verifica:
 * - Procesamiento de mensajes de descubrimiento
 * - Procesamiento de estado de dispositivos
 * - Parsing de payloads de notificación
 * - Manejo de errores
 */
class MqttMessageHandlerTest {

    private lateinit var context: Context
    private lateinit var handler: MqttMessageHandler

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        handler = MqttMessageHandler(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `processMessage handles device discovery response`() = runTest {
        // Given
        val topic = "esp32/response/device123"
        val payload = """{"available": true}"""
        var deviceFound = false
        val onDeviceFound: (String) -> Unit = { deviceFound = true }

        // When
        val result = handler.processMessage(topic, payload, onDeviceFound = onDeviceFound)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        assertTrue("onDeviceFound should have been called", deviceFound)
    }

    @Test
    fun `processMessage handles device status update`() = runTest {
        // Given
        val topic = "esp32/device/device123/status"
        val payload = """{"connected": true}"""
        var statusReceived = false
        var receivedConnected = false
        val onDeviceStatus: (String, Boolean) -> Unit = { _, connected ->
            statusReceived = true
            receivedConnected = connected
        }

        // When
        val result = handler.processMessage(
            topic,
            payload,
            onDeviceStatus = onDeviceStatus
        )

        // Then
        assertTrue("Result should be success", result.isSuccess)
        assertTrue("onDeviceStatus should have been called", statusReceived)
        assertTrue("Device should be connected", receivedConnected)
    }

    @Test
    fun `processMessage handles unknown topic gracefully`() = runTest {
        // Given
        val topic = "unknown/topic"
        val payload = """{"data": "value"}"""

        // When
        val result = handler.processMessage(topic, payload)

        // Then
        assertTrue("Should handle unknown topic gracefully", result.isSuccess)
    }

    @Test
    fun `parseNotificationPayload returns NotificationInfo for valid JSON`() {
        // Given
        val payload = """
            {
                "id": 123,
                "title": "Test Title",
                "content": "Test Content",
                "appName": "Test App",
                "timestamp": 1234567890000
            }
        """.trimIndent()

        // When
        val notification = handler.parseNotificationPayload(payload)

        // Then
        assertNotNull("Should parse notification", notification)
        assertEquals("Should parse title", "Test Title", notification?.title)
        assertEquals("Should parse content", "Test Content", notification?.content)
        assertEquals("Should parse app name", "Test App", notification?.appName)
        assertEquals("Should parse id", 123L, notification?.id)
    }

    @Test
    fun `parseNotificationPayload returns null for invalid JSON`() {
        // Given
        val invalidPayload = "not a json"

        // When
        val notification = handler.parseNotificationPayload(invalidPayload)

        // Then
        assertNull("Should return null for invalid JSON", notification)
    }

    @Test
    fun `parseNotificationPayload handles missing fields with defaults`() {
        // Given
        val minimalPayload = "{}"

        // When
        val notification = handler.parseNotificationPayload(minimalPayload)

        // Then
        assertNotNull("Should handle minimal JSON", notification)
        assertEquals("Should default title to empty", "", notification?.title)
        assertEquals("Should default content to empty", "", notification?.content)
        assertEquals("Should default appName to empty", "", notification?.appName)
    }
}
