package com.dynamictecnologies.notificationmanager.di

import android.content.Context
import com.dynamictecnologies.notificationmanager.data.bluetooth.BluetoothDeviceScanner
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttConnectionManager
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttDeviceLinkManager
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttDeviceScanner
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttMessageHandler
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttNotificationSender
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttReconnectionStrategy
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttSubscriptionManager
import com.dynamictecnologies.notificationmanager.data.repository.DevicePairingRepositoryImpl
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.device.PairDeviceWithTokenUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.device.ScanBluetoothDevicesUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.device.SendNotificationToDeviceUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.device.UnpairDeviceUseCase

/**
 * Módulo de inyección de dependencias manual para componentes Bluetooth y MQTT.
 * 
 * Provee todas las dependencias necesarias para:
 * - Descubrimiento Bluetooth
 * - Vinculación de dispositivos
 * - Comunicación MQTT
 * 
 * - Security: Credenciales desde BuildConfig
 */
object BluetoothMqttModule {
    
    // ========================================
    // BLUETOOTH COMPONENTS
    // ========================================
    
    fun provideBluetoothDeviceScanner(context: Context): BluetoothDeviceScanner {
        return BluetoothDeviceScanner(context)
    }
    
    // ========================================
    // MQTT COMPONENTS
    // ========================================
    
    fun provideMqttConnectionManager(context: Context): MqttConnectionManager {
        return MqttConnectionManager(context)
    }
    
    fun provideMqttNotificationSender(connectionManager: MqttConnectionManager): MqttNotificationSender {
        return MqttNotificationSender(connectionManager)
    }
    
    fun provideMqttDeviceScanner(connectionManager: MqttConnectionManager): MqttDeviceScanner {
        return MqttDeviceScanner(connectionManager)
    }
    
    fun provideMqttMessageHandler(context: Context): MqttMessageHandler {
        return MqttMessageHandler(context)
    }
    
    fun provideMqttSubscriptionManager(connectionManager: MqttConnectionManager): MqttSubscriptionManager {
        return MqttSubscriptionManager(connectionManager)
    }
    
    fun provideMqttReconnectionStrategy(
        connectionManager: MqttConnectionManager,
        subscriptionManager: MqttSubscriptionManager
    ): MqttReconnectionStrategy {
        return MqttReconnectionStrategy(connectionManager, subscriptionManager)
    }
    
    fun provideMqttDeviceLinkManager(
        connectionManager: MqttConnectionManager,
        subscriptionManager: MqttSubscriptionManager
    ): MqttDeviceLinkManager {
        return MqttDeviceLinkManager(connectionManager, subscriptionManager)
    }
    
    // ========================================
    // REPOSITORIES (SINGLETONS)
    // ========================================
    
    @Volatile
    private var devicePairingRepository: DevicePairingRepository? = null
    
    /**
     * Provee singleton de DevicePairingRepository.
     * IMPORTANTE: Debe ser singleton para que UI y servicio compartan los mismos datos.
     */
    fun provideDevicePairingRepository(context: Context): DevicePairingRepository {
        return devicePairingRepository ?: synchronized(this) {
            devicePairingRepository ?: DevicePairingRepositoryImpl(context.applicationContext).also {
                devicePairingRepository = it
            }
        }
    }
    
    // ========================================
    // USE CASES
    // ========================================
    
    fun provideScanBluetoothDevicesUseCase(bluetoothScanner: BluetoothDeviceScanner): ScanBluetoothDevicesUseCase {
        return ScanBluetoothDevicesUseCase(bluetoothScanner)
    }
    
    fun providePairDeviceWithTokenUseCase(
        pairingRepository: DevicePairingRepository,
        mqttConnectionManager: MqttConnectionManager
    ): PairDeviceWithTokenUseCase {
        return PairDeviceWithTokenUseCase(pairingRepository, mqttConnectionManager)
    }
    
    fun provideSendNotificationToDeviceUseCase(
        pairingRepository: DevicePairingRepository,
        mqttSender: MqttNotificationSender
    ): SendNotificationToDeviceUseCase {
        return SendNotificationToDeviceUseCase(pairingRepository, mqttSender)
    }
    
    fun provideUnpairDeviceUseCase(
        pairingRepository: DevicePairingRepository,
        mqttConnectionManager: MqttConnectionManager
    ): UnpairDeviceUseCase {
        return UnpairDeviceUseCase(pairingRepository, mqttConnectionManager)
    }
    
    // ========================================
    // VIEW MODEL FACTORIES
    // ========================================
    
    fun provideDevicePairingViewModelFactory(context: Context): com.dynamictecnologies.notificationmanager.viewmodel.DevicePairingViewModelFactory {
        val bluetoothScanner = provideBluetoothDeviceScanner(context)
        val mqttConnectionManager = provideMqttConnectionManager(context)
        val pairingRepository = provideDevicePairingRepository(context)
        
        return com.dynamictecnologies.notificationmanager.viewmodel.DevicePairingViewModelFactory(
            scanBluetoothDevicesUseCase = provideScanBluetoothDevicesUseCase(bluetoothScanner),
            pairDeviceUseCase = providePairDeviceWithTokenUseCase(pairingRepository, mqttConnectionManager),
            unpairDeviceUseCase = provideUnpairDeviceUseCase(pairingRepository, mqttConnectionManager),
            pairingRepository = pairingRepository
        )
    }
}
