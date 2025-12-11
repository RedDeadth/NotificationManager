package com.dynamictecnologies.notificationmanager.test.domain.usecases

import com.dynamictecnologies.notificationmanager.domain.entities.DevicePairing
import com.dynamictecnologies.notificationmanager.domain.entities.InvalidTokenException
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.device.PairDeviceWithTokenUseCase
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttConnectionManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para PairDeviceWithTokenUseCase.
 * 
 * Verifica:
 * - Validación de token
 * - Guardado correcto
 * - Conexión MQTT
 * - Manejo de errores
 */
class PairDeviceWithTokenUseCaseTest {
    
    private lateinit var pairingRepository: DevicePairingRepository
    private lateinit var mqttConnectionManager: MqttConnectionManager
    private lateinit var useCase: PairDeviceWithTokenUseCase
    
    @Before
    fun setup() {
        pairingRepository = mockk(relaxed = true)
        mqttConnectionManager = mockk(relaxed = true)
        useCase = PairDeviceWithTokenUseCase(pairingRepository, mqttConnectionManager)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `invoke with valid token should save pairing and connect MQTT`() = runTest {
        // Given
        val deviceName = "ESP32_A3F9"
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val token = "A3F9K2L7"
        
        // Mock suspend functions explicitly - relaxed doesn't work well with Result<T>
        coEvery { pairingRepository.savePairing(any()) } returns Result.success(Unit)
        coEvery { mqttConnectionManager.connect() } coAnswers { Result.success(Unit) }
        
        // When
        val result = useCase(deviceName, deviceAddress, token)
        
        // Then
        assertTrue("Result should be success", result.isSuccess)
        
        coVerify {
            pairingRepository.savePairing(
                match {
                    it.bluetoothName == deviceName &&
                    it.bluetoothAddress == "AA:BB:CC:DD:EE:FF" &&
                    it.token == "A3F9K2L7" &&
                    it.mqttTopic == "n/A3F9K2L7"
                }
            )
        }
        
        coVerify { mqttConnectionManager.connect() }
    }
    
    @Test
    fun `invoke with invalid token should fail with InvalidTokenException`() = runTest {
        // Given
        val invalidToken = "SHORT"  // Menos de 8 caracteres
        
        // When
        val result = useCase("ESP32_A3F9", "AA:BB:CC:DD:EE:FF", invalidToken)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidTokenException)
        
        coVerify(exactly = 0) { pairingRepository.savePairing(any()) }
        coVerify(exactly = 0) { mqttConnectionManager.connect() }
    }
    
    @Test
    fun `invoke with lowercase token should normalize to uppercase`() = runTest {
        // Given
        val lowercaseToken = "a3f9k2l7"
        
        coEvery { pairingRepository.savePairing(any()) } returns Result.success(Unit)
        coEvery { mqttConnectionManager.connect() } coAnswers { Result.success(Unit) }
        
        // When
        val result = useCase("ESP32", "AA:BB:CC:DD:EE:FF", lowercaseToken)
        
        // Then
        assertTrue("Result should be success", result.isSuccess)
        
        coVerify {
            pairingRepository.savePairing(
                match { it.token == "A3F9K2L7" }  // Uppercase
            )
        }
    }
    
    @Test
    fun `invoke should fail if repository save fails`() = runTest {
        // Given
        val token = "A3F9K2L7"
        val error = Exception("Database error")
        
        coEvery { pairingRepository.savePairing(any()) } returns Result.failure(error)
        
        // When
        val result = useCase("ESP32", "AA:BB:CC:DD:EE:FF", token)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        
        // MQTT no debería intentarse si save falla
        coVerify(exactly = 0) { mqttConnectionManager.connect() }
    }
    
    @Test
    fun `invoke should fail if MQTT connection fails`() = runTest {
        // Given
        val token = "A3F9K2L7"
        val mqttError = Exception("MQTT connection error")
        
        coEvery { pairingRepository.savePairing(any()) } returns Result.success(Unit)
        coEvery { mqttConnectionManager.connect() } coAnswers { Result.failure(mqttError) }
        
        // When
        val result = useCase("ESP32", "AA:BB:CC:DD:EE:FF", token)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        assertEquals(mqttError, result.exceptionOrNull())
    }
    
    @Test
    fun `invoke with special characters in token should fail`() = runTest {
        // Given
        val invalidToken = "A3F@K2L7"  // Contiene @
        
        // When
        val result = useCase("ESP32", "AA:BB:CC:DD:EE:FF", invalidToken)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidTokenException)
    }
    
    @Test
    fun `invoke should generate correct MQTT topic format`() = runTest {
        // Given
        val token = "TEST1234"
        
        coEvery { pairingRepository.savePairing(any()) } returns Result.success(Unit)
        coEvery { mqttConnectionManager.connect() } coAnswers { Result.success(Unit) }
        
        // When
        useCase("ESP32", "AA:BB:CC:DD:EE:FF", token)
        
        // Then
        coVerify {
            pairingRepository.savePairing(
                match { it.mqttTopic == "n/TEST1234" }
            )
        }
    }
}
