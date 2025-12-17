package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.dynamictecnologies.notificationmanager.domain.entities.DevicePairing
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación simple de DevicePairingRepository usando SharedPreferences.
 * 
 * Sin Firebase, sin complejidad, sin sincronización.
 * Solo almacenamiento local persistente.
 * 
 * - Clean Architecture: Implementación en capa de datos
 */
class DevicePairingRepositoryImpl(
    context: Context
) : DevicePairingRepository {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _currentPairing = MutableStateFlow<DevicePairing?>(loadPairing())
    
    override fun getCurrentPairing(): Flow<DevicePairing?> = _currentPairing.asStateFlow()
    
    override suspend fun savePairing(pairing: DevicePairing): Result<Unit> {
        return try {
            prefs.edit().apply {
                putString(KEY_BT_NAME, pairing.bluetoothName)
                putString(KEY_BT_ADDRESS, pairing.bluetoothAddress)
                putString(KEY_TOKEN, pairing.token)
                putString(KEY_MQTT_TOPIC, pairing.mqttTopic)
                putLong(KEY_PAIRED_AT, pairing.pairedAt)
                apply()
            }
            _currentPairing.value = pairing
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun clearPairing(): Result<Unit> {
        return try {
            prefs.edit().clear().apply()
            _currentPairing.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getMqttTopic(): String? {
        return _currentPairing.value?.mqttTopic
    }
    
    override suspend fun hasPairedDevice(): Boolean {
        return _currentPairing.value != null
    }
    
    /**
     * Carga el pairing guardado desde SharedPreferences.
     * Si el token guardado es inválido (ej: formato viejo de 8 chars),
     * limpia los datos y retorna null.
     */
    private fun loadPairing(): DevicePairing? {
        val btName = prefs.getString(KEY_BT_NAME, null) ?: return null
        val btAddress = prefs.getString(KEY_BT_ADDRESS, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val mqttTopic = prefs.getString(KEY_MQTT_TOPIC, null) ?: return null
        val pairedAt = prefs.getLong(KEY_PAIRED_AT, 0L)
        
        if (pairedAt == 0L) return null
        
        return try {
            DevicePairing(
                bluetoothName = btName,
                bluetoothAddress = btAddress,
                token = token,
                mqttTopic = mqttTopic,
                pairedAt = pairedAt
            )
        } catch (e: IllegalArgumentException) {
            // Token inválido (probablemente formato viejo de 8 chars)
            // Limpiar datos viejos
            android.util.Log.w("DevicePairingRepo", "Token inválido encontrado, limpiando: ${e.message}")
            prefs.edit().clear().apply()
            null
        }
    }
    
    companion object {
        private const val PREFS_NAME = "device_pairing"
        private const val KEY_BT_NAME = "bt_name"
        private const val KEY_BT_ADDRESS = "bt_address"
        private const val KEY_TOKEN = "token"
        private const val KEY_MQTT_TOPIC = "mqtt_topic"
        private const val KEY_PAIRED_AT = "paired_at"
    }
}
