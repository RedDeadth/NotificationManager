package com.dynamictecnologies.notificationmanager.di

import android.content.Context
import com.dynamictecnologies.notificationmanager.BuildConfig
import com.dynamictecnologies.notificationmanager.data.bluetooth.BluetoothDeviceScanner
import com.dynamictecnologies.notificationmanager.data.mqtt.MqttConnectionManager
import com.dynamictecnologies.notificationmanager.data.mqtt.MqttNotificationPublisher
import com.dynamictecnologies.notificationmanager.data.repository.DevicePairingRepositoryImpl
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.device.PairDeviceWithTokenUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.device.ScanBluetoothDevicesUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.device.SendNotificationToDeviceUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.device.UnpairDeviceUseCase

/**
 * Módulo de inyección de dependencias para componentes Bluetooth y MQTT.
 * 
 * Provee todas las dependencias necesarias para:
 * - Descubrimiento Bluetooth
 * - Vinculación de dispositivos
 * - Publicación MQTT
 * 
 * Principios aplicados:
 * - DIP: Centraliza creación de dependencias
 * - SRP: Solo provee dependencias de Bluetooth/MQTT
 * - Security: Lee credenciales de BuildConfig
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
    
    /**
     * Provee MqttConnectionManager con credenciales desde BuildConfig
     */
    fun provideMqttConnectionManager(): MqttConnectionManager {
        return MqttConnectionManager(
            brokerUrl = BuildConfig.MQTT_BROKER,
            username = BuildConfig.MQTT_USERNAME,
            password = BuildConfig.MQTT_PASSWORD
        )
    }
    
    fun provideMqttNotificationPublisher(
        connectionManager: MqttConnectionManager
    ): MqttNotificationPublisher {
        return MqttNotificationPublisher(connectionManager)
    }
    
    // ========================================
    // REPOSITORIES
    // ========================================
    
    fun provideDevicePairingRepository(context: Context): DevicePairingRepository {
        return DevicePairingRepositoryImpl(context)
    }
    
    // ========================================
    // USE CASES
    // ========================================
    
    fun provideScanBluetoothDevicesUseCase(
        bluetoothScanner: BluetoothDeviceScanner
    ): ScanBluetoothDevicesUseCase {
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
        mqttPublisher: MqttNotificationPublisher
    ): SendNotificationToDeviceUseCase {
        return SendNotificationToDeviceUseCase(pairingRepository, mqttPublisher)
    }
    
    fun provideUnpairDeviceUseCase(
        pairingRepository: DevicePairingRepository,
        mqttConnectionManager: MqttConnectionManager
    ): UnpairDeviceUseCase {
        return UnpairDeviceUseCase(pairingRepository, mqttConnectionManager)
    }
    
    // ========================================
    // CONVENIENCE: Full Stack Provider
    // ========================================
    
    /**
     * Provee todo el stack de dependencias listo para usar
     */
    fun provideDevicePairingStack(context: Context): DevicePairingStack {
        val bluetoothScanner = provideBluetoothDeviceScanner(context)
        val mqttConnectionManager = provideMqttConnectionManager()
        val mqttPublisher = provideMqttNotificationPublisher(mqttConnectionManager)
        val pairingRepository = provideDevicePairingRepository(context)
        
        return DevicePairingStack(
            scanBluetoothDevicesUseCase = provideScanBluetoothDevicesUseCase(bluetoothScanner),
            pairDeviceUseCase = providePairDeviceWithTokenUseCase(pairingRepository, mqttConnectionManager),
            sendNotificationUseCase = provideSendNotificationToDeviceUseCase(pairingRepository, mqttPublisher),
            unpairDeviceUseCase = provideUnpairDeviceUseCase(pairingRepository, mqttConnectionManager),
            pairingRepository = pairingRepository
        )
    }
    
    /**
     * Contenedor de todos los use cases relacionados con device pairing
     */
    data class DevicePairingStack(
        val scanBluetoothDevicesUseCase: ScanBluetoothDevicesUseCase,
        val pairDeviceUseCase: PairDeviceWithTokenUseCase,
        val sendNotificationUseCase: SendNotificationToDeviceUseCase,
        val unpairDeviceUseCase: UnpairDeviceUseCase,
        val pairingRepository: DevicePairingRepository
    )
}
