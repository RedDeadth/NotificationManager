package com.dynamictecnologies.notificationmanager.domain.repositories

import com.dynamictecnologies.notificationmanager.domain.entities.DevicePairing
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestión de dispositivos vinculados.
 * 
 * Almacenamiento local únicamente (SharedPreferences).
 * Sin Firebase, sin sincronización remota.
 * 
 * - Clean Architecture: Sin dependencias de implementación
 */
interface DevicePairingRepository {
    /**
     * Guarda un nuevo emparejamiento
     */
    suspend fun savePairing(pairing: DevicePairing): Result<Unit>
    
    /**
     * Obtiene el dispositivo actualmente vinculado
     */
    fun getCurrentPairing(): Flow<DevicePairing?>
    
    /**
     * Elimina vinculación actual
     */
    suspend fun clearPairing(): Result<Unit>
    
    /**
     * Obtiene el topic MQTT para publicar notificaciones
     * @return Topic en formato "n/TOKEN" o null si no hay dispositivo vinculado
     */
    suspend fun getMqttTopic(): String?
    
    /**
     * Verifica si hay un dispositivo vinculado actualmente
     */
    suspend fun hasPairedDevice(): Boolean
}
