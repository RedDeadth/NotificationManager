package com.dynamictecnologies.notificationmanager.domain.usecases.user

import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.withTimeout

class AwaitFirebaseUserUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(timeoutMs: Long = 5000): Result<FirebaseUser> {
        return try {
            val user = withTimeout(timeoutMs) {
                authRepository.awaitFirebaseUser()
                    ?: throw Exception("Usuario no disponible después del timeout")
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}