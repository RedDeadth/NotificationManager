```kotlin
package com.dynamictecnologies.notificationmanager.util

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService

object PermissionHelper {
    private const val TAG = "PermissionHelper"

    /**
     * Verifica si los permisos de NotificationListener están activos
     */
    fun hasNotificationListenerPermission(context: Context): Boolean {
        return NotificationListenerService.isNotificationListenerEnabled(context)
    }
    
    /**
     * Verifica si los permisos de Bluetooth están otorgados
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requiere BLUETOOTH_SCAN y BLUETOOTH_CONNECT
            hasPermission(context, Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android < 12 requiere BLUETOOTH y BLUETOOTH_ADMIN
            hasPermission(context, Manifest.permission.BLUETOOTH) &&
            hasPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
        }
    }
    
    /**
     * Verifica si el permiso de notificaciones está otorgado (Android 13+)
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true  // No requerido en versiones anteriores
        }
    }
    
    /**
     * Obtiene lista de permisos Bluetooth requeridos según versión de Android
     */
    fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }
    
    /**
     * Verifica si todos los permisos requeridos están otorgados
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasBluetoothPermissions(context) && hasPostNotificationsPermission(context)
    }
    
    /**
     * Helper privado para verificar un permiso específico
     */
    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Muestra diálogo para solicitar permisos de NotificationListener
     */
    fun showNotificationPermissionDialog(context: Context) {
        try {
            AlertDialog.Builder(context)
                .setTitle("🔔 Permisos necesarios")
                .setMessage(
                    "Para recolectar notificaciones, esta aplicación necesita acceso especial.\n\n" +
                            "📱 **Pasos a seguir:**\n" +
                            "1. Toca 'Ir a configuración'\n" +
                            "2. Busca 'Notification Manager' en la lista\n" +
                            "3. Activa el interruptor junto al nombre\n" +
                            "4. Toca 'Permitir' en el diálogo de confirmación\n" +
                            "5. Regresa a la app\n\n" +
                            "⚠️ **Sin estos permisos NO se pueden recolectar notificaciones**"
                )
                .setPositiveButton("Ir a configuración") { _, _ ->
                    openNotificationListenerSettings(context)
                }
                .setNegativeButton("Más tarde") { dialog, _ ->
                    dialog.dismiss()
                    Log.d(TAG, "Usuario pospuso configuración de permisos")
                }
                .setCancelable(false)
                .show()

            Log.d(TAG, "📱 Diálogo de permisos mostrado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error mostrando diálogo: ${e.message}")
        }
    }

    /**
     * Abre la configuración de acceso a notificaciones
     */
    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "✅ Abriendo configuración de NotificationListener")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error abriendo configuración principal: ${e.message}")

            // Fallback: abrir configuración general de seguridad
            try {
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
                Log.d(TAG, "✅ Abriendo configuración de seguridad como fallback")
            } catch (fallbackException: Exception) {
                Log.e(TAG, "❌ Error abriendo configuración de seguridad: ${fallbackException.message}")

                // Último fallback: configuración general
                try {
                    val lastResortIntent = Intent(Settings.ACTION_SETTINGS)
                    lastResortIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(lastResortIntent)
                    Log.d(TAG, "✅ Abriendo configuración general como último recurso")
                } catch (lastException: Exception) {
                    Log.e(TAG, "❌ Error crítico: No se puede abrir ninguna configuración: ${lastException.message}")
                }
            }
        }
    }

    /**
     * Muestra instrucciones detalladas para encontrar la configuración manualmente
     */
    fun showManualInstructions(context: Context) {
        try {
            AlertDialog.Builder(context)
                .setTitle("🔍 Instrucciones manuales")
                .setMessage(
                    "Si no encuentras la configuración automáticamente:\n\n" +
                            "📱 **Búsqueda manual:**\n" +
                            "1. Ve a 'Configuración' de tu dispositivo\n" +
                            "2. Busca 'Aplicaciones' o 'Apps'\n" +
                            "3. Busca 'Acceso especial' o 'Permisos especiales'\n" +
                            "4. Busca 'Acceso de notificaciones' o 'Notification access'\n" +
                            "5. Encuentra 'Notification Manager'\n" +
                            "6. Activa el interruptor\n\n" +
                            "📍 **Ubicaciones comunes:**\n" +
                            "• Configuración → Notificaciones → Acceso de notificaciones\n" +
                            "• Configuración → Seguridad → Acceso de notificaciones\n" +
                            "• Configuración → Aplicaciones → Permisos especiales"
                )
                .setPositiveButton("Entendido", null)
                .show()

            Log.d(TAG, "📖 Instrucciones manuales mostradas")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error mostrando instrucciones: ${e.message}")
        }
    }

    /**
     * Verifica el estado de permisos y registra información detallada
     */
    fun checkAndLogPermissionStatus(context: Context) {
        val hasPermissions = hasNotificationListenerPermission(context)

        if (hasPermissions) {
            Log.d(TAG, "✅ Estado de permisos: ACTIVOS")
            Log.d(TAG, "🔔 La app puede recolectar notificaciones")
        } else {
            Log.w(TAG, "❌ Estado de permisos: INACTIVOS")
            Log.w(TAG, "⚠️ La app NO puede recolectar notificaciones")
            Log.w(TAG, "📱 Es necesario otorgar permisos en: Configuración → Notificaciones → Acceso de notificaciones")
        }

        // Log adicional para debugging
        try {
            val packageName = context.packageName
            val appName = context.packageManager.getApplicationLabel(context.applicationInfo)
            Log.d(TAG, "📦 Package: $packageName")
            Log.d(TAG, "📱 App name: $appName")
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo info de la app: ${e.message}")
        }
    }

    /**
     * Crea un intent para mostrar el diálogo de permisos
     */
    fun createPermissionRequestIntent(): Intent {
        return Intent("com.dynamictecnologies.notificationmanager.NEED_PERMISSIONS")
    }
}
```