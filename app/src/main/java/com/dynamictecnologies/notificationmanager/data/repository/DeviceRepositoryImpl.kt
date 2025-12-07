package com.dynamictecnologies.notificationmanager.data.repository

import com.dynamictecnologies.notificationmanager.data.datasource.firebase.DeviceDataSource
import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación de DeviceRepository usando Firebase como backend.
 * 
 * Responsabilidad única: Coordinar operaciones de dispositivos.
 * 
 * Principios aplicados:
 * - SRP: Solo coordina device operations
 * - DIP: Implementa interfaz del domain
 * - Clean Architecture: Data layer implementa domain contracts
 */
class DeviceRepositoryImpl(
    private val deviceDataSource: DeviceDataSource
) : DeviceRepository {
    
    private val _connectedDevice = MutableStateFlow<DeviceInfo?>(null)
    override val connectedDevice: Flow<DeviceInfo?> = _connectedDevice.asStateFlow()
    
    override suspend fun connectToDevice(deviceId: String, userId: String): Result<Unit> {
        return try {
            // Buscar username
            val usernameResult = getUsernameByUid(userId)
            val username = usernameResult.getOrNull()
            
            // Obtener email si hay username
            var email: String? = null
            if (username != null) {
                val userInfo = deviceDataSource.getUserInfo(username)
                email = userInfo.getOrNull()?.second
            }
            
            // Registrar dispositivo si no existe
            deviceDataSource.registerDeviceIfNeeded(deviceId)
            
            // Vincular dispositivo
            val linkResult = deviceDataSource.linkDeviceToUser(
                deviceId,
                userId,
                username,
                email
            )
            
            if (linkResult.isSuccess) {
                // Actualizar estado local
                _connectedDevice.value = DeviceInfo(
                    id = deviceId,
                    name = "ESP32 Visualizador",
                    isConnected = true,
                    lastSeen = System.currentTimeMillis()
                )
            }
            
            linkResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun unlinkDevice(): Result<Unit> {
        val device = _connectedDevice.value ?: return Result.failure(
            Exception("No device connected")
        )
        
        return try {
            val result = deviceDataSource.unlinkDevice(device.id)
            
            if (result.isSuccess) {
                _connectedDevice.value = null
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getUsernameByUid(uid: String): Result<String> {
        return deviceDataSource.findUsernameByUid(uid)
    }
    
    override fun observeDeviceNotifications(
        userId: String,
        deviceId: String
    ): Flow<List<NotificationInfo>> {
        // TODO: Implement Firebase notifications listener
        // For now, return empty flow
        return MutableStateFlow(emptyList())
    }
}
