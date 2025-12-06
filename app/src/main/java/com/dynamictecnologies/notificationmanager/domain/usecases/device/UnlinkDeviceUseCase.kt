package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.domain.repositories.DeviceRepository

/**
 * Use Case para desvincular el dispositivo actualmente conectado.
 * 
 * Responsabilidad única: Gestionar la desvinculación de dispositivos.
 * 
 * Principios aplicados:
 * - SRP: Solo desvincula dispositivos
 * - DIP: Depende de abstracción
 * - Clean Architecture: Domain layer
 */
class UnlinkDeviceUseCase(
    private val deviceRepository: DeviceRepository
) {
    /**
     * Desvincula el dispositivo actualmente conectado.
     * 
     * @return Result<Unit> Success si se desvincula correctamente
     */
    suspend operator fun invoke(): Result<Unit> {
        return deviceRepository.unlinkDevice()
    }
}
