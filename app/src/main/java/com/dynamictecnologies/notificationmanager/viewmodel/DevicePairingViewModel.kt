package com.dynamictecnologies.notificationmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.bluetooth.BluetoothDeviceScanner
import com.dynamictecnologies.notificationmanager.di.BluetoothMqttModule
import com.dynamictecnologies.notificationmanager.domain.entities.DevicePairing
import com.dynamictecnologies.notificationmanager.domain.useCases.device.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel para gestión de vinculación de dispositivos Bluetooth/MQTT.
 * 
 * Responsabilidades:
 * - Escaneo de dispositivos Bluetooth
 * - Vinculación con token
 * - Desvinculación
 * - Estado de UI
 * 
 * Principios aplicados:
 * - SRP: Solo gestión de pairing
 * - Reactive: StateFlow para UI reactiva
 * - Clean Architecture: Usa Use Cases del dominio
 */
class DevicePairingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val devicePairingStack = BluetoothMqttModule.provideDevicePairingStack(application)
    
    // Use Cases
    private val scanBluetoothDevicesUseCase = devicePairingStack.scanBluetoothDevicesUseCase
    private val pairDeviceUseCase = devicePairingStack.pairDeviceUseCase
    private val unpairDeviceUseCase = devicePairingStack.unpairDeviceUseCase
    private val pairingRepository = devicePairingStack.pairingRepository
    
    // Estados UI
    val bluetoothDevices: StateFlow<List<BluetoothDeviceScanner.ScannedDevice>> = 
        scanBluetoothDevicesUseCase.devices.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val isScanning: StateFlow<Boolean> = 
        scanBluetoothDevicesUseCase.isScanning.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    val currentPairing: StateFlow<DevicePairing?> = 
        pairingRepository.getCurrentPairing().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    private val _showTokenDialog = MutableStateFlow(false)
    val showTokenDialog: StateFlow<Boolean> = _showTokenDialog.asStateFlow()
    
    private val _selectedDevice = MutableStateFlow<BluetoothDeviceScanner.ScannedDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDeviceScanner.ScannedDevice?> = _selectedDevice.asStateFlow()
    
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    /**
     * Inicia escaneo de dispositivos Bluetooth
     */
    fun startBluetoothScan() {
        scanBluetoothDevicesUseCase.startScan().onFailure { error ->
            _pairingState.value = PairingState.Error(
                error.message ?: "Error iniciando escaneo Bluetooth"
            )
        }
    }
    
    /**
     * Detiene escaneo
     */
    fun stopBluetoothScan() {
        scanBluetoothDevicesUseCase.stopScan()
    }
    
    /**
     * Muestra dialog para ingresar token
     */
    fun showTokenDialog(device: BluetoothDeviceScanner.ScannedDevice) {
        _selectedDevice.value = device
        _showTokenDialog.value = true
        stopBluetoothScan()  // Detener escaneo al seleccionar dispositivo
    }
    
    /**
     * Cierra dialog de token
     */
    fun dismissTokenDialog() {
        _showTokenDialog.value = false
        _selectedDevice.value = null
        _pairingState.value = PairingState.Idle
    }
    
    /**
     * Vincula dispositivo con token
     */
    fun pairDevice(token: String) {
        val device = _selectedDevice.value ?: return
        
        _pairingState.value = PairingState.Pairing
        
        viewModelScope.launch {
            pairDeviceUseCase(
                bluetoothName = device.name,
                bluetoothAddress = device.address,
                token = token
            ).onSuccess {
                _pairingState.value = PairingState.Success
                _showTokenDialog.value = false
                _selectedDevice.value = null
            }.onFailure { error ->
                _pairingState.value = PairingState.Error(
                    error.message ?: "Error vinculando dispositivo"
                )
            }
        }
    }
    
    /**
     * Desvincula dispositivo actual
     */
    fun unpairDevice() {
        _pairingState.value = PairingState.Pairing
        
        viewModelScope.launch {
            unpairDeviceUseCase().onSuccess {
                _pairingState.value = PairingState.Success
            }.onFailure { error ->
                _pairingState.value = PairingState.Error(
                    error.message ?: "Error desvinculando dispositivo"
                )
            }
        }
    }
    
    /**
     * Reinicia estado de pairing
     */
    fun resetPairingState() {
        _pairingState.value = PairingState.Idle
    }
    
    override fun onCleared() {
        super.onCleared()
        stopBluetoothScan()
    }
    
    /**
     * Estados de la interfaz de pairing
     */
    sealed class PairingState {
        object Idle : PairingState()
        object Pairing : PairingState()
        object Success : PairingState()
        data class Error(val message: String) : PairingState()
    }
}
