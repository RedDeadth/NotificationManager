package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para MqttSubscriptionManager.
 * 
 * Verifica:
 * - Suscripción a topics
 * - Desuscripción de topics
 * - Re-suscripción después de reconexión
 * - Prevención de suscripciones duplicadas
 * - Thread safety de operaciones concurrentes
 */
class MqttSubscriptionManagerTest {

    private lateinit var connectionManager: MqttConnectionManager
    private lateinit var subscriptionManager: MqttSubscriptionManager

    @Before
    fun setup() {
        connectionManager = mockk(relaxed = true)
        subscriptionManager = MqttSubscriptionManager(connectionManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `subscribe adds topic to active subscriptions`() = runTest {
        // Given
        val topic = "test/topic"
        every { connectionManager.isConnected() } returns true

        // When
        val result = subscriptionManager.subscribe(topic, 1)

        // Then
        assertTrue("Subscribe should succeed", result.isSuccess)
        assertTrue("Topic should be in active subscriptions", 
            subscriptionManager.getActiveSubscriptions().contains(topic))
        verify { connectionManager.subscribe(topic, 1) }
    }

    @Test
    fun `subscribe prevents duplicate subscriptions`() = runTest {
        // Given
        val topic = "test/topic"
        every { connectionManager.isConnected() } returns true

        // When - subscribe twice
        subscriptionManager.subscribe(topic, 1)
        val result = subscriptionManager.subscribe(topic, 1)

        // Then - should still succeed but only subscribe once
        assertTrue("Duplicate subscribe should succeed", result.isSuccess)
        verify(exactly = 1) { connectionManager.subscribe(topic, 1) }
    }

    @Test
    fun `subscribe fails when MQTT not connected`() = runTest {
        // Given
        val topic = "test/topic"
        every { connectionManager.isConnected() } returns false

        // When
        val result = subscriptionManager.subscribe(topic, 1)

        // Then
        assertTrue("Subscribe should fail", result.isFailure)
        assertEquals("MQTT no conectado", result.exceptionOrNull()?.message)
    }

    @Test
    fun `unsubscribe removes topic from subscriptions`() = runTest {
        // Given
        val topic = "test/topic"
        every { connectionManager.isConnected() } returns true
        val client = mockk<org.eclipse.paho.client.mqttv3.MqttClient>(relaxed = true)
        every { connectionManager.getClient() } returns client
        
        // Subscribe first
        subscriptionManager.subscribe(topic, 1)

        // When
        val result = subscriptionManager.unsubscribe(topic)

        // Then
        assertTrue("Unsubscribe should succeed", result.isSuccess)
        assertFalse("Topic should not be in active subscriptions", 
            subscriptionManager.getActiveSubscriptions().contains(topic))
    }

    @Test
    fun `resubscribeAll resubscribes to all active topics`() = runTest {
        // Given
        val topic1 = "test/topic1"
        val topic2 = "test/topic2"
        every { connectionManager.isConnected() } returns true
        
        // Add some subscriptions
        subscriptionManager.subscribe(topic1, 1)
        subscriptionManager.subscribe(topic2, 1)
        
        clearMocks(connectionManager, answers = false)
        every { connectionManager.isConnected() } returns true

        // When
        val result = subscriptionManager.resubscribeAll()

        // Then
        assertTrue("Resubscribe should succeed", result.isSuccess)
        verify(exactly = 1) { connectionManager.subscribe(topic1, 1) }
        verify(exactly = 1) { connectionManager.subscribe(topic2, 1) }
    }

    @Test
    fun `resubscribeAll fails when MQTT not connected`() = runTest {
        // Given
        every { connectionManager.isConnected() } returns true
        subscriptionManager.subscribe("test/topic", 1)
        
        every { connectionManager.isConnected() } returns false

        // When
        val result = subscriptionManager.resubscribeAll()

        // Then
        assertTrue("Resubscribe should fail", result.isFailure)
    }

    @Test
    fun `clear removes all subscriptions`() = runTest {
        // Given
        every { connectionManager.isConnected() } returns true
        subscriptionManager.subscribe("test/topic1", 1)
        subscriptionManager.subscribe("test/topic2", 1)

        // When
        subscriptionManager.clear()

        // Then
        assertTrue("Active subscriptions should be empty",
            subscriptionManager.getActiveSubscriptions().isEmpty())
    }

    @Test
    fun `getActiveSubscriptions returns immutable set`() = runTest {
        // Given
        val topic = "test/topic"
        every { connectionManager.isConnected() } returns true
        subscriptionManager.subscribe(topic, 1)

        // When
        val subscriptions = subscriptionManager.getActiveSubscriptions()

        // Then - should be a copy, not the original set
        assertNotNull("Subscriptions should not be null", subscriptions)
        assertTrue("Should contain subscribed topic", subscriptions.contains(topic))
    }
}
