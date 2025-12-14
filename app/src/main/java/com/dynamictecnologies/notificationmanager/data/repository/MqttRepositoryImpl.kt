package com.dynamictecnologies.notificationmanager.data.repository

import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.*
import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.MqttRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Implementación concreta de MqttRepository.
 * 
 * Coordina los diferentes managers MQTT para proporcionar funcionalidad completa.
 * 
 * - Facade Pattern: Simplifica uso de múltiples managers
 * - Clean Architecture: Data layer implementa contratos de domain
 */
class MqttRepositoryImpl(
    private val connectionManager: MqttConnectionManager,
    private val messageHandler: MqttMessageHandler,
    private val deviceScanner: MqttDeviceScanner,
    private val notificationSender: MqttNotificationSender
) : MqttRepository {
    
    override val connectionStatus: Flow<Boolean>
        get() = connectionManager.connectionStatus
    
    override suspend fun connect(): Result<Unit> {
        return connectionManager.connect()
    }
    
    override suspend fun disconnect() {
        connectionManager.disconnect()
    }
    
    override fun isConnected(): Boolean {
        return connectionManager.isConnected()
    }
    
    override suspend fun sendNotification(notification: NotificationInfo): Result<Unit> {
        // Para enviar, necesitamos un deviceId que debería venir del flujo de dispositivo conectado
        // Por ahora, esto es una simplificación. En la implementación completa,
        // esto vendría del DeviceRepository
        return Result.failure(Exception("Requiere deviceId del DeviceRepository"))
    }
    
    override suspend fun searchDevices(): Result<List<DeviceInfo>> {
        return deviceScanner.searchDevices().map {
            // Por ahora retorna lista vacía ya que los dispositivos se encontrarán vía callbacks
            // En implementación completa, esto coordinará con Firebase
            emptyList()
        }
    }
    
    override fun setCurrentUserId(userId: String) {
        notificationSender.setCurrentUser(userId, null)
    }
    
    override fun setCurrentUsername(username: String) {
        // Actualizar con el username
        // Necesitaríamos mantener el userId también
    }
}
