package com.dynamictecnologies.notificationmanager.data.permissions

import android.content.Context
import android.content.Intent
import android.util.Log
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService

/**
 * Verificador de permisos de notificaciones.
 * 
 * Responsabilidad única: Verificar permisos de NotificationListener
 * y notificar a la UI cuando falten.
 * 
 * Principios aplicados:
 * - SRP: Solo gestión de permisos
 * - Observable: Emite broadcast cuando faltan permisos
 * - Stateless: Solo verifica, no mantiene estado
 */
class NotificationPermissionChecker(private val context: Context) {
    
    /**
     * Verifica si el permiso de NotificationListener está otorgado
     */
    fun hasPermission(): Boolean {
        return NotificationListenerService.isNotificationListenerEnabled(context)
    }
    
    /**
     * Verifica permisos y notifica si faltan
     */
    fun checkAndNotify(): Boolean {
        val hasPermissions = hasPermission()
        Log.d(TAG, "Estado de permisos: $hasPermissions")
        
        if (!hasPermissions) {
            Log.w(TAG, "⚠️ NotificationListener no está habilitado")
            notifyPermissionsNeeded()
        }
        
        return hasPermissions
    }
    
    /**
     * Fuerza verificación de permisos (llamada desde UI)
     */
    fun recheckPermissions(): Boolean {
        Log.d(TAG, "🔄 Reverificando permisos")
        val hasPermissions = checkAndNotify()
        
        if (hasPermissions) {
            Log.d(TAG, "✅ Permisos confirmados")
        } else {
            Log.w(TAG, "❌ Permisos aún no otorgados")
        }
        
        return hasPermissions
    }
    
    /**
     * Notifica a la UI que se necesitan permisos
     */
    private fun notifyPermissionsNeeded() {
        val intent = Intent(ACTION_NEED_PERMISSIONS)
        context.sendBroadcast(intent)
        Log.d(TAG, "📱 Broadcast enviado: se necesitan permisos")
    }
    
    companion object {
        private const val TAG = "NotifPermissionChecker"
        const val ACTION_NEED_PERMISSIONS = "com.dynamictecnologies.notificationmanager.NEED_PERMISSIONS"
    }
}
