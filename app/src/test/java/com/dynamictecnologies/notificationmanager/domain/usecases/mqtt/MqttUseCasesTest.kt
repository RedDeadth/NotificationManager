package com.dynamictecnologies.notificationmanager.domain.usecases.mqtt

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.MqttRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Tests unitarios para MQTT Use Cases.
 */
class SendNotificationViaMqttUseCaseTest {

    private lateinit var mockRepository: MqttRepository
    private lateinit var useCase: SendNotificationViaMqttUseCase

    @Before
    fun setup() {
        mockRepository = mockk()
        useCase = SendNotificationViaMqttUseCase(mockRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invoke calls repository sendNotification`() = runTest {
        // Given
        val notification = NotificationInfo(
            id = 1L,
            title = "Test",
            content = "Content",
            appName = "App",
            timestamp = Date()
        )
        coEvery { mockRepository.sendNotification(notification) } returns Result.success(Unit)

        // When
        val result = useCase(notification)

        // Then
        assertTrue("Should return success", result.isSuccess)
        coVerify(exactly = 1) { mockRepository.sendNotification(notification) }
    }
}

class DisconnectFromMqttUseCaseTest {

    private lateinit var mockRepository: MqttRepository
    private lateinit var useCase: DisconnectFromMqttUseCase

    @Before
    fun setup() {
        mockRepository = mockk()
        useCase = DisconnectFromMqttUseCase(mockRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invoke calls repository disconnect and returns success`() = runTest {
        // Given
        coEvery { mockRepository.disconnect() } returns Result.success(Unit)

        // When
        val result = useCase()

        // Then
        assertTrue("Should return success", result.isSuccess)
        coVerify(exactly = 1) { mockRepository.disconnect() }
    }
    
    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        // Given
        val exception = Exception("Disconnect error")
        coEvery { mockRepository.disconnect() } returns Result.failure(exception)

        // When
        val result = useCase()

        // Then
        assertTrue("Should return failure", result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 1) { mockRepository.disconnect() }
    }
}

class SearchDevicesUseCaseTest {

    private lateinit var mockRepository: MqttRepository
    private lateinit var useCase: SearchDevicesUseCase

    @Before
    fun setup() {
        mockRepository = mockk()
        useCase = SearchDevicesUseCase(mockRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invoke returns devices from repository`() = runTest {
        // Given
        coEvery { mockRepository.searchDevices() } returns Result.success(emptyList())

        // When
        val result = useCase()

        // Then
        assertTrue("Should return success", result.isSuccess)
        coVerify(exactly = 1) { mockRepository.searchDevices() }
    }
}
