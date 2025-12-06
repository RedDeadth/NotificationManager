package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.domain.repositories.DeviceRepository

/**
 * Use Case para obtener el username de un usuario por su UID.
 * 
 * Responsabilidad única: Resolver UID a username.
 * 
 * Principios aplicados:
 * - SRP: Solo resuelve username
 * - DIP: Depende de abstracción
 * - Clean Architecture: Domain layer
 */
class GetUsernameByUidUseCase(
    private val deviceRepository: DeviceRepository
) {
    /**
     * Obtiene el username de un usuario dado su UID de Firebase.
     * 
     * @param uid UID de Firebase del usuario
     * @return Result<String> Username o error si no se encuentra
     */
    suspend operator fun invoke(uid: String): Result<String> {
        return deviceRepository.getUsernameByUid(uid)
    }
}
