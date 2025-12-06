package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.DeviceRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use Case para observar el estado de conexión del dispositivo.
 * 
 * Responsabilidad única: Proporcionar flujo de estado de dispositivo conectado.
 * 
 * Principios aplicados:
 * - SRP: Solo observa estado de dispositivo
 * - DIP: Depende de abstracción
 * - Reactive: Usa Flow para cambios reactivos
 */
class ObserveDeviceConnectionUseCase(
    private val deviceRepository: DeviceRepository
) {
    /**
     * Obtiene un Flow del dispositivo actualmente conectado.
     * 
     * @return Flow<DeviceInfo?> Flow que emite el dispositivo conectado o null
     */
    operator fun invoke(): Flow<DeviceInfo?> {
        return deviceRepository.connectedDevice
    }
}
