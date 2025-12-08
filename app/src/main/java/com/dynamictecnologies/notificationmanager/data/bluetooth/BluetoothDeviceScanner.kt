package com.dynamictecnologies.notificationmanager.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scanner de dispositivos Bluetooth cercanos (solo ESP32).
 * 
 * Busca dispositivos cuyo nombre comience con "ESP32" y los agrega
 * a una lista reactiva ordenada por señal (RSSI).
 * 
 * Principios aplicados:
 * - SRP: Solo escanea dispositivos Bluetooth
 * - Clean Code: API simple con Flow reactivo
 * - Error Handling: Result types y validación de permisos
 */
class BluetoothDeviceScanner(private val context: Context) {
    
    /**
     * Representa un dispositivo Bluetooth escaneado
     */
    data class ScannedDevice(
        val name: String,
        val address: String,
        val rssi: Int  // Signal strength (-100 a 0, mayor = más cerca)
    )
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanOptimizer = com.dynamictecnologies.notificationmanager.util.BluetoothScanOptimizer(context)
    
    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private var isReceiverRegistered = false
    private var scanStartTime: Long = 0
    
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = 
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    
                    device?.let {
                        try {
                            if (hasBluetoothPermissions()) {
                                val deviceName = it.name
                                if (deviceName?.startsWith("ESP32") == true) {
                                    addDevice(ScannedDevice(
                                        name = deviceName,
                                        address = it.address,
                                        rssi = rssi
                                    ))
                                    Log.d(TAG, "Dispositivo ESP32 encontrado: $deviceName (RSSI: $rssi)")
                                }
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Error de permisos al obtener nombre del dispositivo", e)
                        }
                    }
                }
                
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Escaneo Bluetooth finalizado. ${_devices.value.size} dispositivos encontrados")
                    _isScanning.value = false
                }
            }
        }
    }
    
    /**
     * Inicia escaneo de dispositivos Bluetooth con optimización de batería
     */
    fun startScan(): Result<Unit> {
        // Verificar si se debe permitir escaneo (modo ahorro de energía)
        if (!scanOptimizer.shouldAllowScan()) {
            return Result.failure(IllegalStateException("Escaneo Bluetooth deshabilitado en modo ahorro de energía"))
        }
        
        if (!hasBluetoothPermissions()) {
            return Result.failure(SecurityException("Permisos Bluetooth no otorgados"))
        }
        
        if (bluetoothAdapter == null) {
            return Result.failure(IllegalStateException("Bluetooth no disponible en este dispositivo"))
        }
        
        if (!bluetoothAdapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth está deshabilitado"))
        }
        
        try {
            // Limpiar resultados previos
            _devices.value = emptyList()
            
            // Registrar receiver si no está registrado
            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.registerReceiver(receiver, filter)
                isReceiverRegistered = true
            }
            
            // Cancelar escaneo previo si existe
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            
            // Iniciar discovery con duración optimizada
            val started = bluetoothAdapter.startDiscovery()
            if (started) {
                scanStartTime = System.currentTimeMillis()
                _isScanning.value = true
                
                val recommendedDuration = scanOptimizer.getRecommendedScanDuration()
                Log.d(TAG, "Escaneo Bluetooth iniciado (duración recomendada: ${recommendedDuration}ms)")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("No se pudo iniciar el escaneo Bluetooth"))
            }
            
        } catch (e: SecurityException) {
            return Result.failure(e)
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Detiene el escaneo actual
     */
    fun stopScan() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            if (isReceiverRegistered) {
                context.unregisterReceiver(receiver)
                isReceiverRegistered = false
            }
            _isScanning.value = false
            Log.d(TAG, "Escaneo Bluetooth detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo escaneo: ${e.message}")
        }
    }
    
    /**
     * Agrega o actualiza un dispositivo en la lista
     */
    private fun addDevice(device: ScannedDevice) {
        val currentDevices = _devices.value.toMutableList()
        val existingIndex = currentDevices.indexOfFirst { it.address == device.address }
        
        if (existingIndex != -1) {
            // Actualizar RSSI si el dispositivo ya existe
            currentDevices[existingIndex] = device
        } else {
            // Agregar nuevo dispositivo
            currentDevices.add(device)
        }
        
        // Ordenar por señal (RSSI más alto primero)
        _devices.value = currentDevices.sortedByDescending { it.rssi }
    }
    
    /**
     * Verifica si hay permisos Bluetooth
     */
    private fun hasBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android < 12
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Limpia recursos
     */
    fun cleanup() {
        stopScan()
        _devices.value = emptyList()
    }
    
    companion object {
        private const val TAG = "BluetoothScanner"
    }
}
