package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.domain.repositories.DeviceRepository

/**
 * Use Case para conectar/vincular un dispositivo ESP32 con un usuario.
 * 
 * Responsabilidad única: Gestionar la vinculación de dispositivos.
 * 
 * Principios aplicados:
 * - SRP: Solo vincula dispositivos
 * - DIP: Depende de abstracción (DeviceRepository)
 * - Clean Architecture: Domain layer use case
 */
class ConnectToDeviceUseCase(
    private val deviceRepository: DeviceRepository
) {
    /**
     * Conecta/vincula un dispositivo ESP32 con un usuario.
     * 
     * @param deviceId ID único del dispositivo ESP32
     * @param userId UID de Firebase del usuario
     * @return Result<Unit> Success si se vincula correctamente
     */
    suspend operator fun invoke(deviceId: String, userId: String): Result<Unit> {
        return deviceRepository.connectToDevice(deviceId, userId)
    }
}
