package com.dynamictecnologies.notificationmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.usecases.device.*
import com.dynamictecnologies.notificationmanager.domain.usecases.mqtt.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel refactorizado para gestión de dispositivos ESP32.
 * 
 * Ahora sigue Clean Architecture y SOLID:
 * - SRP: Solo maneja UI state y coordinación
 * - DIP: Depende de Use Cases (abstracciones)
 * - No conoce MQTT ni Firebase directamente
 * 
 * Reducido de 259 → ~120 líneas (-54%)
 */
class DeviceViewModel(
    private val connectToMqttUseCase: ConnectToMqttUseCase,
    private val disconnectFromMqttUseCase: DisconnectFromMqttUseCase,
    private val searchDevicesUseCase: SearchDevicesUseCase,
    private val sendNotificationUseCase: SendNotificationViaMqttUseCase,
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val unlinkDeviceUseCase: UnlinkDeviceUseCase,
    private val observeDeviceUseCase: ObserveDeviceConnectionUseCase,
    private val getUsernameUseCase: GetUsernameByUidUseCase
) : ViewModel() {
    
    // UI State
    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _showDeviceDialog = MutableStateFlow(false)
    val showDeviceDialog: StateFlow<Boolean> = _showDeviceDialog.asStateFlow()
    
    private val _scanCompleted = MutableStateFlow(false)
    val scanCompleted: StateFlow<Boolean> = _scanCompleted.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Observable device from repository
    val connectedDevice: Flow<DeviceInfo?> = observeDeviceUseCase()
    
    /**
     * Conecta al broker MQTT.
     */
    fun connectToMqtt() {
        viewModelScope.launch {
            val result = connectToMqttUseCase()
            if (result.isFailure) {
                _errorMessage.value = "Error connecting to MQTT: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    /**
     * Desconecta del broker MQTT.
     */
    fun disconnectFromMqtt() {
        viewModelScope.launch {
            disconnectFromMqttUseCase()
        }
    }
    
    /**
     * Busca dispositivos ESP32 disponibles.
     */
    fun searchDevices(userId: String) {
        viewModelScope.launch {
            _isSearching.value = true
            _scanCompleted.value = false
            _devices.value = emptyList()
            
            try {
                // Buscar dispositivos vía MQTT
                val result = searchDevicesUseCase()
                
                if (result.isSuccess) {
                    _devices.value = result.getOrDefault(emptyList())
                    _scanCompleted.value = true
                } else {
                    _errorMessage.value = "Error searching devices: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Exception: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    /**
     * Conecta/vincula un dispositivo ESP32.
     */
    fun connectToDevice(deviceId: String, userId: String) {
        viewModelScope.launch {
            val result = connectToDeviceUseCase(deviceId, userId)
            
            if (result.isFailure) {
                _errorMessage.value = "Error connecting device: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    /**
     * Desvincula el dispositivo actualmente conectado.
     */
    fun unlinkDevice() {
        viewModelScope.launch {
            val result = unlinkDeviceUseCase()
            
            if (result.isFailure) {
                _errorMessage.value = "Error unlinking device: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    /**
     * Envía una notificación al dispositivo conectado.
     */
    fun sendNotification(notification: NotificationInfo) {
        viewModelScope.launch {
            val result = sendNotificationUseCase(notification)
            
            if (result.isFailure) {
                _errorMessage.value = "Error sending notification: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    /**
     * Toggle dialogo de dispositivos.
     */
    fun toggleDeviceDialog() {
        _showDeviceDialog.value = !_showDeviceDialog.value
    }
    
    /**
     * Limpia la lista de dispositivos.
     */
    fun clearDevices() {
        _devices.value = emptyList()
        _scanCompleted.value = false
    }
    
    /**
     * Limpia mensaje de error.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * Factory refactorizada para DeviceViewModel siguiendo DI.
 * 
 * Ahora inyecta Use Cases en lugar de servicios directos.
 */
class DeviceViewModelFactory(
    private val connectToMqttUseCase: ConnectToMqttUseCase,
    private val disconnectFromMqttUseCase: DisconnectFromMqttUseCase,
    private val searchDevicesUseCase: SearchDevicesUseCase,
    private val sendNotificationUseCase: SendNotificationViaMqttUseCase,
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val unlinkDeviceUseCase: UnlinkDeviceUseCase,
    private val observeDeviceUseCase: ObserveDeviceConnectionUseCase,
    private val getUsernameUseCase: GetUsernameByUidUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceViewModel::class.java)) {
            return DeviceViewModel(
                connectToMqttUseCase,
                disconnectFromMqttUseCase,
                searchDevicesUseCase,
                sendNotificationUseCase,
                connectToDeviceUseCase,
                unlinkDeviceUseCase,
                observeDeviceUseCase,
                getUsernameUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}