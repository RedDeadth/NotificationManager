package com.dynamictecnologies.notificationmanager.domain.usecases.mqtt

import com.dynamictecnologies.notificationmanager.domain.repositories.MqttRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para ConnectToMqttUseCase.
 */
class ConnectToMqttUseCaseTest {

    private lateinit var mockRepository: MqttRepository
    private lateinit var useCase: ConnectToMqttUseCase

    @Before
    fun setup() {
        mockRepository = mockk()
        useCase = ConnectToMqttUseCase(mockRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invoke returns success when repository connects successfully`() = runTest {
        // Given
        coEvery { mockRepository.connect() } returns Result.success(Unit)

        // When
        val result = useCase()

        // Then
        assertTrue("Should return success", result.isSuccess)
        coVerify(exactly = 1) { mockRepository.connect() }
    }

    @Test
    fun `invoke returns failure when repository fails to connect`() = runTest {
        // Given
        val exception = Exception("Connection failed")
        coEvery { mockRepository.connect() } returns Result.failure(exception)

        // When
        val result = useCase()

        // Then
        assertTrue("Should return failure", result.isFailure)
        assertEquals("Should have same exception", exception, result.exceptionOrNull())
        coVerify(exactly = 1) { mockRepository.connect() }
    }
}
