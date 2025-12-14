package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.data.bluetooth.BluetoothDeviceScanner
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para escanear dispositivos Bluetooth ESP32 cercanos.
 * 
 * Wrapper simple alrededor de BluetoothDeviceScanner para
 * mantener consistencia con patrón de Use Cases.
 * 
 * - Clean Architecture: Use Case en dominio
 */
class ScanBluetoothDevicesUseCase(
    private val bluetoothScanner: BluetoothDeviceScanner
) {
    
    /**
     * Lista reactiva de dispositivos encontrados
     */
    val devices: Flow<List<BluetoothDeviceScanner.ScannedDevice>> = 
        bluetoothScanner.devices
    
    /**
     * Estado de escaneo
     */
    val isScanning: Flow<Boolean> = bluetoothScanner.isScanning
    
    /**
     * Inicia el escaneo de dispositivos
     */
    fun startScan(): Result<Unit> {
        return bluetoothScanner.startScan()
    }
    
    /**
     * Detiene el escaneo
     */
    fun stopScan() {
        bluetoothScanner.stopScan()
    }
}
