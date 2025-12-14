package com.dynamictecnologies.notificationmanager.domain.repositories

import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstraction para operaciones MQTT.
 * 
 * Encapsula toda la lógica de comunicación MQTT con el broker y dispositivos ESP32.
 * 
 * - Clean Architecture: Define contrato en capa de dominio
 */
interface MqttRepository {
    /**
     * Estado de conexión MQTT.
     * Fluye true cuando está conectado, false cuando está desconectado.
     */
    val connectionStatus: Flow<Boolean>
    
    /**
     * Conecta al broker MQTT.
     * 
     * @return Result<Unit> - Success si conecta, Failure con excepción si falla
     */
    suspend fun connect(): Result<Unit>
    
    /**
     * Desconecta del broker MQTT y limpia recursos.
     */
    suspend fun disconnect()
    
    /**
     * Verifica si actualmente está conectado al broker.
     * 
     * @return true si está conectado
     */
    fun isConnected(): Boolean
    
    /**
     * Envía una notificación a través de MQTT al dispositivo conectado.
     * 
     * @param notification La notificación a enviar
     * @return Result<Unit> - Success si se envía, Failure si falla
     */
    suspend fun sendNotification(notification: NotificationInfo): Result<Unit>
    
    /**
     * Busca dispositivos ESP32 disponibles en la red.
     * 
     * @return Result<List<DeviceInfo>> - Lista de dispositivos encontrados o error
     */
    suspend fun searchDevices(): Result<List<DeviceInfo>>
    
    /**
     * Establece el ID del usuario actual para identificación.
     * 
     * @param userId UID de Firebase del usuario
     */
    fun setCurrentUserId(userId: String)
    
    /**
     * Establece el username del usuario actual.
     * 
     * @param username Nombre de usuario
     */
    fun setCurrentUsername(username: String)
}
