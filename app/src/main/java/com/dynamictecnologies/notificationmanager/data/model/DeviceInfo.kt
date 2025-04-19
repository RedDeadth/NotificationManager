package com.dynamictecnologies.notificationmanager.data.model

data class DeviceInfo(
    val id: String,
    val name: String = "ESP32 Visualizador",
    val isConnected: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
) 