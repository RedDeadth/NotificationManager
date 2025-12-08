package com.dynamictecnologies.notificationmanager.test.domain.usecases

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.mqtt.MqttNotificationPublisher
import com.dynamictecnologies.notificationmanager.domain.entities.NoDevicePairedException
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.device.SendNotificationToDeviceUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests unitarios para SendNotificationToDeviceUseCase.
 * 
 * Verifica:
 * - Envío correcto con dispositivo vinculado
 * - Error cuando no hay dispositivo
 * - Propagación de errores MQTT
 */
class SendNotificationToDeviceUseCaseTest {
    
    private lateinit var pairingRepository: DevicePairingRepository
    private lateinit var mqttPublisher: MqttNotificationPublisher
    private lateinit var useCase: SendNotificationToDeviceUseCase
    
    @Before
    fun setup() {
        pairingRepository = mockk()
        mqttPublisher = mockk()
        useCase = SendNotificationToDeviceUseCase(pairingRepository, mqttPublisher)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `invoke with paired device should publish to correct topic`() = runTest {
        // Given
        val topic = "n/A3F9K2L7"
        val notification = NotificationInfo(
            appName = "Banco",
            title = "Transacción",
            content = "Retiro $500",
            timestamp = Date()
        )
        
        coEvery { pairingRepository.getMqttTopic() } returns topic
        coEvery { mqttPublisher.publish(topic, notification) } returns Result.success(Unit)
        
        // When
        val result = useCase(notification)
        
        // Then
        assertTrue(result.isSuccess)
        
        coVerify { pairingRepository.getMqttTopic() }
        coVerify { mqttPublisher.publish(topic, notification) }
    }
    
    @Test
    fun `invoke without paired device should fail with NoDevicePairedException`() = runTest {
        // Given
        val notification = NotificationInfo(
            appName = "Test",
            title = "Test",
            content = "Test",
            timestamp = Date()
        )
        
        coEvery { pairingRepository.getMqttTopic() } returns null
        
        // When
        val result = useCase(notification)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoDevicePairedException)
        
        coVerify(exactly = 0) { mqttPublisher.publish(any(), any()) }
    }
    
    @Test
    fun `invoke should propagate MQTT publish errors`() = runTest {
        // Given
        val topic = "n/TEST1234"
        val notification = NotificationInfo(
            appName = "App",
            title = "Title",
            content = "Content",
            timestamp = Date()
        )
        val mqttError = Exception("MQTT not connected")
        
        coEvery { pairingRepository.getMqttTopic() } returns topic
        coEvery { mqttPublisher.publish(topic, notification) } returns Result.failure(mqttError)
        
        // When
        val result = useCase(notification)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(mqttError, result.exceptionOrNull())
    }
    
    @Test
    fun `invoke should handle notifications with empty content`() = runTest {
        // Given - aunque el ViewModel debería prevenir esto, el use case debe manejarlo
        val topic = "n/TOKEN123"
        val notification = NotificationInfo(
            appName = "App",
            title = "",
            content = "",
            timestamp = Date()
        )
        
        coEvery { pairingRepository.getMqttTopic() } returns topic
        coEvery { mqttPublisher.publish(topic, notification) } returns Result.success(Unit)
        
        // When
        val result = useCase(notification)
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { mqttPublisher.publish(topic, notification) }
    }
}
