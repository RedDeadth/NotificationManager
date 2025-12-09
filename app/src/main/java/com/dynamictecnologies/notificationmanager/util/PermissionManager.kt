package com.dynamictecnologies.notificationmanager.util

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Manager centralizado para manejo de permisos de notificaciones.
 * 
 * Extrado de MainActivity para aplicar SRP.
 * 
 * Responsabilidades:
 * - Verificar permisos de NotificationListener
 * - Verificar permiso POST_NOTIFICATIONS (Android 13+)
 * - Mostrar diálogos de permisos
 * 
 * Principios aplicados:
 * - SRP: Solo maneja lógica de permisos
 * - DRY: Centraliza checks duplicados
 * - Testable: Sin dependencias de Activity
 */
object PermissionManager {
    
    /**
     * Verifica si la app tiene permiso de NotificationListener activo.
     */
    fun hasNotificationListenerPermission(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(packageName) == true
    }
    
    /**
     * Verifica si la app tiene permiso POST_NOTIFICATIONS (Android 13+).
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No se requiere en Android < 13
        }
    }
    
    /**
     * Muestra diálogo explicando por qué se necesita el permiso de NotificationListener.
     */
    fun showNotificationPermissionDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Permiso Requerido")
            .setMessage(
                "Esta app necesita acceso a las notificaciones para:\n\n" +
                "• Capturar notificaciones del sistema\n" +
                "• Enviarlas a tu dispositivo ESP32\n\n" +
                "Por favor, habilita el acceso en la siguiente pantalla."
            )
            .setPositiveButton("Configurar") { dialog, _ ->
                dialog.dismiss()
                openNotificationListenerSettings(context)
            }
            .setNegativeButton("Ahora no") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Abre la pantalla de configuración de NotificationListener.
     */
    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
