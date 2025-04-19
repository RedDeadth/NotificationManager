package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.service.MqttService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class DeviceViewModel(
    private val context: Context,
    private val mqttService: MqttService
) : ViewModel() {
    private val TAG = "DeviceViewModel"
    
    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _showDeviceDialog = MutableStateFlow(false)
    val showDeviceDialog: StateFlow<Boolean> = _showDeviceDialog.asStateFlow()
    
    private val _connectedDevice = MutableStateFlow<DeviceInfo?>(null)
    val connectedDevice: StateFlow<DeviceInfo?> = _connectedDevice.asStateFlow()
    
    private val _scanCompleted = MutableStateFlow(false)
    val scanCompleted: StateFlow<Boolean> = _scanCompleted.asStateFlow()
    
    // Referencia a Firebase
    private val database = FirebaseDatabase.getInstance()
    
    init {
        viewModelScope.launch {
            mqttService.connectedDevice.collectLatest { device ->
                _connectedDevice.value = device
            }
        }
    }
    
    fun connectToMqtt() {
        viewModelScope.launch(Dispatchers.IO) {
            mqttService.connect()
        }
    }
    
    fun disconnectFromMqtt() {
        viewModelScope.launch(Dispatchers.IO) {
            mqttService.disconnect()
        }
    }
    
    fun searchDevices(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            _scanCompleted.value = false
            _devices.value = emptyList()
            
            try {
                // Guardar userId en el servicio MQTT
                mqttService.setCurrentUserId(userId)
                
                // Buscar username para el userId
                val username = findUsernameByUid(userId)
                username?.let {
                    mqttService.setCurrentUsername(it)
                }
                
                mqttService.searchDevices()
                
                // Buscar dispositivos en Firebase
                val realDevices = mutableListOf<DeviceInfo>()
                val dispositvosRef = database.getReference("dispositivos")
                val snapshot = dispositvosRef.get().await()
                
                if (snapshot.exists()) {
                    for (deviceSnapshot in snapshot.children) {
                        val deviceId = deviceSnapshot.key ?: continue
                        val disponible = deviceSnapshot.child("disponible").getValue(Boolean::class.java) ?: false
                        val vinculado = deviceSnapshot.child("vinculado").getValue(Boolean::class.java) ?: false
                        
                        if (disponible) {
                            realDevices.add(
                                DeviceInfo(
                                    id = deviceId,
                                    name = "ESP32 Visualizador",
                                    isConnected = vinculado
                                )
                            )
                        }
                    }
                }
                
                // Si hay dispositivos reales, los usamos
                if (realDevices.isNotEmpty()) {
                    _devices.value = realDevices
                } else {
                    // Si no, añadimos uno de demostración
                    val demoDevice = DeviceInfo(
                        id = "ESP32_" + UUID.randomUUID().toString().substring(0, 8),
                        name = "ESP32 Visualizador (Demo)",
                        isConnected = false
                    )
                    _devices.value = listOf(demoDevice)
                }
                
                // Esperar un poco para completar la búsqueda
                kotlinx.coroutines.delay(2000)
                _scanCompleted.value = true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error buscando dispositivos", e)
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    private suspend fun findUsernameByUid(uid: String): String? {
        return try {
            val snapshot = database.getReference("usernames").orderByValue().equalTo(uid).get().await()
            if (snapshot.exists() && snapshot.childrenCount > 0) {
                snapshot.children.first().key
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando username por UID", e)
            null
        }
    }
    
    fun connectToDevice(deviceId: String, userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Guardar userId para referencias futuras
                mqttService.setCurrentUserId(userId)
                
                // Buscar username para el userId si no se ha hecho antes
                val username = findUsernameByUid(userId)
                username?.let {
                    mqttService.setCurrentUsername(it)
                }
                
                mqttService.connectToDevice(deviceId, userId)
            } catch (e: Exception) {
                Log.e(TAG, "Error conectando al dispositivo: ${e.message}")
            }
        }
    }
    
    fun sendNotification(notification: NotificationInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            mqttService.sendNotification(notification)
        }
    }
    
    fun toggleDeviceDialog() {
        _showDeviceDialog.value = !_showDeviceDialog.value
    }
    
    fun clearDevices() {
        _devices.value = emptyList()
        _scanCompleted.value = false
    }
    
    fun setCurrentUserId(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Guardar userId en el servicio MQTT
                mqttService.setCurrentUserId(userId)
                
                // Buscar username para el userId
                val username = findUsernameByUid(userId)
                username?.let {
                    mqttService.setCurrentUsername(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error configurando usuario: ${e.message}")
            }
        }
    }
}

class DeviceViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceViewModel::class.java)) {
            val mqttService = MqttService(context)
            return DeviceViewModel(context, mqttService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 