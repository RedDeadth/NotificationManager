package com.dynamictecnologies.notificationmanager.domain.repositories

import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstraction para operaciones con dispositivos ESP32.
 * 
 * Maneja la vinculación de dispositivos con usuarios y sincronización con Firebase.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja operaciones de dispositivos
 * - DIP: Abstracción para uso en capa de dominio
 * - ISP: Interfaz segregada para dispositivos
 * - Clean Architecture: Contrato definido en dominio
 */
interface DeviceRepository {
    /**
     * Dispositivo actualmente conectado.
     * Flow que emite el dispositivo conectado o null si no hay ninguno.
     */
    val connectedDevice: Flow<DeviceInfo?>
    
    /**
     * Conecta/vincula un dispositivo ESP32 con un usuario.
     * 
     * @param deviceId ID único del dispositivo ESP32
     * @param userId UID de Firebase del usuario
     * @return Result<Unit> - Success si se vincula, Failure con error
     */
    suspend fun connectToDevice(deviceId: String, userId: String): Result<Unit>
    
    /**
     * Desvincula el dispositivo actualmente conectado.
     * 
     * @return Result<Unit> - Success si se desvincula correctamente
     */
    suspend fun unlinkDevice(): Result<Unit>
    
    /**
     * Obtiene el username de un usuario dado su UID.
     * 
     * @param uid UID de Firebase del usuario
     * @return Result<String> - Username o error si no se encuentra
     */
    suspend fun getUsernameByUid(uid: String): Result<String>
    
    /**
     * Observa las notificaciones de un usuario en un dispositivo específico.
     * 
     * @param userId UID del usuario
     * @param deviceId ID del dispositivo
     * @return Flow de notificaciones del dispositivo
     */
    fun observeDeviceNotifications(userId: String, deviceId: String): Flow<List<NotificationInfo>>
}
