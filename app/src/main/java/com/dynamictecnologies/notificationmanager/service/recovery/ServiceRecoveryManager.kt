package com.dynamictecnologies.notificationmanager.service.recovery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gestor de recuperación de servicios.
 * 
 * Maneja la lógica de recuperación cuando el NotificationListenerService
 * falla o se desactiva. Implementa reintentos con backoff exponencial.
 * 
 * Principios aplicados:
 * - SRP: Solo recuperación de servicios
 * - Strategy Pattern: Usa BackgroundServiceStrategy para OEM-specific logic
 * - Observable: Notifica estado de recuperación
 */
class ServiceRecoveryManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onRecoveryStateChanged: (RecoveryState) -> Unit = {}
) {
    private val backoffManager = ExponentialBackoffManager(
        initialDelayMs = 1000L,
        maxDelayMs = 300000L,  // 5 minutos max
        multiplier = 2.0
    )
    
    private var isRecovering = false
    
    /**
     * Verifica si el NotificationListenerService está habilitado
     */
    fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = context.packageName
        return enabledListeners?.contains(packageName) == true
    }
    
    /**
     * Intenta recuperar el servicio
     */
    fun attemptRecovery() {
        if (isRecovering) {
            Log.w(TAG, "Already recovering, skipping")
            return
        }
        
        isRecovering = true
        onRecoveryStateChanged(RecoveryState.Recovering)
        
        scope.launch {
            try {
                performRecovery()
                backoffManager.reset()
                isRecovering = false
                onRecoveryStateChanged(RecoveryState.Recovered)
            } catch (e: Exception) {
                Log.e(TAG, "Recovery failed", e)
                handleRecoveryFailure()
            }
        }
    }
    
    /**
     * Realiza la recuperación del servicio
     */
    private suspend fun performRecovery() {
        Log.i(TAG, "Attempting service recovery...")
        
        // 1. Verificar permisos
        if (!isNotificationListenerEnabled()) {
            Log.e(TAG, "Notification listener not enabled")
            throw IllegalStateException("Notification listener permission not granted")
        }
        
        // 2. Toggle notification listener
        toggleNotificationListenerService()
        
        // 3. Esperar para que tome efecto
        backoffManager.delayNext()
        
        // 4. Verificar si funcionó
        if (!isNotificationListenerEnabled()) {
            throw IllegalStateException("Service still not enabled after recovery attempt")
        }
        
        Log.i(TAG, "✅ Service recovery successful")
    }
    
    /**
     * Toggle del NotificationListenerService usando utilidad centralizada
     */
    private suspend fun toggleNotificationListenerService() {
        com.dynamictecnologies.notificationmanager.service.util.NotificationListenerToggler.toggle(
            context = context,
            delayMs = 500L
        )
        Log.d(TAG, "Toggled NotificationListenerService via Toggler")
    }
    
    /**
     * Maneja fallo de recuperación
     */
    private fun handleRecoveryFailure() {
        scope.launch {
            if (backoffManager.hasReachedMaxRetries(MAX_RECOVERY_ATTEMPTS)) {
                Log.e(TAG, "Max recovery attempts reached, giving up")
                isRecovering = false
                onRecoveryStateChanged(RecoveryState.Failed)
            } else {
                Log.w(TAG, "Recovery attempt ${backoffManager.getRetryCount()} failed, will retry")
                backoffManager.delayNext()
                isRecovering = false
                attemptRecovery()  // Reintentar
            }
        }
    }
    
    /**
     * Reinicia el gestor de recuperación
     */
    fun reset() {
        isRecovering = false
        backoffManager.reset()
        onRecoveryStateChanged(RecoveryState.Idle)
    }
    
    /**
     * Estados de recuperación
     */
    sealed class RecoveryState {
        object Idle : RecoveryState()
        object Recovering : RecoveryState()
        object Recovered : RecoveryState()
        object Failed : RecoveryState()
    }
    
    companion object {
        private const val TAG = "ServiceRecovery"
        private const val MAX_RECOVERY_ATTEMPTS = 5
    }
}
