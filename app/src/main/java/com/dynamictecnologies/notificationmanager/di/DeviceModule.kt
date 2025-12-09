package com.dynamictecnologies.notificationmanager.di

import android.content.Context
import com.dynamictecnologies.notificationmanager.data.datasource.firebase.DeviceDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.*
import com.dynamictecnologies.notificationmanager.data.repository.DeviceRepositoryImpl
import com.dynamictecnologies.notificationmanager.data.repository.MqttRepositoryImpl
import com.dynamictecnologies.notificationmanager.domain.repositories.DeviceRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.MqttRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.device.*
import com.dynamictecnologies.notificationmanager.domain.usecases.mqtt.*

/**
 * Dependency Injection Module for Device and MQTT components.
 * 
 * Provides all necessary dependencies following Clean Architecture.
 * 
 * Principios aplicados:
 * - DI Pattern: Centraliza creación de dependencias
 * - SRP: Solo provee dependencias
 * - DIP: Retorna abstracciones cuando es posible
 */
object DeviceModule {
    
    // MQTT Managers (Singletons para mantener estado)
    @Volatile
    private var mqttConnectionManager: MqttConnectionManager? = null
    
    @Volatile
    private var mqttMessageHandler: MqttMessageHandler? = null
    
    @Volatile
    private var mqttDeviceScanner: MqttDeviceScanner? = null
    
    @Volatile
    private var mqttNotificationSender: MqttNotificationSender? = null
    
    fun provideMqttConnectionManager(context: Context): MqttConnectionManager {
        return mqttConnectionManager ?: synchronized(this) {
            mqttConnectionManager ?: MqttConnectionManager(context).also {
                mqttConnectionManager = it
            }
        }
    }
    
    fun provideMqttMessageHandler(context: Context): MqttMessageHandler {
        return mqttMessageHandler ?: synchronized(this) {
            mqttMessageHandler ?: MqttMessageHandler(context).also {
                mqttMessageHandler = it
            }
        }
    }
    
    fun provideMqttDeviceScanner(context: Context): MqttDeviceScanner {
        return mqttDeviceScanner ?: synchronized(this) {
            mqttDeviceScanner ?: MqttDeviceScanner(
                provideMqttConnectionManager(context)
            ).also {
                mqttDeviceScanner = it
            }
        }
    }
    
    fun provideMqttNotificationSender(context: Context): MqttNotificationSender {
        return mqttNotificationSender ?: synchronized(this) {
            mqttNotificationSender ?: MqttNotificationSender(
                provideMqttConnectionManager(context)
            ).also {
                mqttNotificationSender = it
            }
        }
    }
    
    // Repositories
    fun provideMqttRepository(context: Context): MqttRepository {
        return MqttRepositoryImpl(
            connectionManager = provideMqttConnectionManager(context),
            messageHandler = provideMqttMessageHandler(context),
            deviceScanner = provideMqttDeviceScanner(context),
            notificationSender = provideMqttNotificationSender(context)
        )
    }
    
    fun provideDeviceDataSource(): DeviceDataSource {
        return DeviceDataSource()
    }
    
    /**
     * Provee DeviceRepository con componentes MQTT.
     * 
     * Inyecta:
     * - DeviceDataSource: Para operaciones Firebase de dispositivos (solo metadata)
     * - MqttConnectionManager: Para conexiones MQTT
     * - MqttDeviceLinkManager: Para linking de dispositivos via MQTT
     * 
     * NOTA: Firebase solo se usa para autenticación.
     * Todas las notificaciones van directamente via MQTT a ESP32.
     */
    fun provideDeviceRepository(context: Context): DeviceRepository {
        val deviceDataSource = provideDeviceDataSource()
        
        // Componentes MQTT del BluetoothMqttModule
        val mqttConnectionManager = BluetoothMqttModule.provideMqttConnectionManager(context)
        val mqttSubscriptionManager = BluetoothMqttModule.provideMqttSubscriptionManager(mqttConnectionManager)
        val mqttDeviceLinkManager = BluetoothMqttModule.provideMqttDeviceLinkManager(
            mqttConnectionManager,
            mqttSubscriptionManager
        )
        
        return DeviceRepositoryImpl(
            deviceDataSource,
            mqttConnectionManager,
            mqttDeviceLinkManager
        )
    }
    
    // MQTT Use Cases
    fun provideConnectToMqttUseCase(context: Context): ConnectToMqttUseCase {
        return ConnectToMqttUseCase(provideMqttRepository(context))
    }
    
    fun provideDisconnectFromMqttUseCase(context: Context): DisconnectFromMqttUseCase {
        return DisconnectFromMqttUseCase(provideMqttRepository(context))
    }
    
    fun provideSearchDevicesUseCase(context: Context): SearchDevicesUseCase {
        return SearchDevicesUseCase(provideMqttRepository(context))
    }
    
    fun provideSendNotificationViaMqttUseCase(context: Context): SendNotificationViaMqttUseCase {
        return SendNotificationViaMqttUseCase(provideMqttRepository(context))
    }
    
    // Device Use Cases
    fun provideConnectToDeviceUseCase(context: Context): ConnectToDeviceUseCase {
        return ConnectToDeviceUseCase(provideDeviceRepository(context))
    }
    
    fun provideUnlinkDeviceUseCase(context: Context): UnlinkDeviceUseCase {
        return UnlinkDeviceUseCase(provideDeviceRepository(context))
    }
    
    fun provideObserveDeviceConnectionUseCase(context: Context): ObserveDeviceConnectionUseCase {
        return ObserveDeviceConnectionUseCase(provideDeviceRepository(context))
    }
    
    fun provideGetUsernameByUidUseCase(context: Context): GetUsernameByUidUseCase {
        return GetUsernameByUidUseCase(provideDeviceRepository(context))
    }
}
