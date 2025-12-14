package com.dynamictecnologies.notificationmanager.util.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dynamictecnologies.notificationmanager.MainActivity
import com.dynamictecnologies.notificationmanager.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Razones por las que el servicio puede detenerse
 */
sealed class ServiceStopReason {
    object SystemKilled : ServiceStopReason()
    object UnexpectedCrash : ServiceStopReason()
    object PermissionRevoked : ServiceStopReason()
    object BatteryOptimization : ServiceStopReason()
    object ManufacturerRestriction : ServiceStopReason()
    data class Unknown(val details: String) : ServiceStopReason()
}

/**
 * Gestor de notificaciones para informar al usuario sobre el estado del servicio.
 * 
 */
class ServiceCrashNotifier(private val context: Context) {
    
    companion object {
        private const val TAG = "ServiceCrashNotifier"
        private const val CHANNEL_ID = "service_crash_channel"
        private const val CHANNEL_NAME = "Alertas del Servicio"
        private const val NOTIFICATION_ID_CRASH = 2000
        private const val NOTIFICATION_ID_RECOVERY = 2001
        
        // Actions
        const val ACTION_DISMISS = "com.dynamictecnologies.notificationmanager.ACTION_DISMISS_CRASH"
        const val ACTION_RESTART = "com.dynamictecnologies.notificationmanager.ACTION_RESTART_SERVICE"
        const val ACTION_SETTINGS = "com.dynamictecnologies.notificationmanager.ACTION_OPEN_SETTINGS"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Crea el canal de notificaciones para alertas del servicio
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones importantes sobre el estado del servicio de monitoreo"
                enableVibration(true)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Canal de notificaciones de crash creado")
        }
    }
    
    /**
     * Muestra una notificación informando que el servicio se detuvo inesperadamente
     */
    fun showCrashNotification(reason: ServiceStopReason, lastActivity: Long? = null) {
        Log.w(TAG, "Mostrando notificación de crash: $reason")
        
        val reasonText = when (reason) {
            is ServiceStopReason.SystemKilled -> 
                "El sistema detuvo el servicio"
            is ServiceStopReason.UnexpectedCrash -> 
                "El servicio se cerró inesperadamente"
            is ServiceStopReason.PermissionRevoked -> 
                "Los permisos fueron revocados"
            is ServiceStopReason.BatteryOptimization -> 
                "Optimización de batería detuvo el servicio"
            is ServiceStopReason.ManufacturerRestriction -> 
                "Restricciones del fabricante detuvieron el servicio"
            is ServiceStopReason.Unknown -> 
                "El servicio se detuvo: ${reason.details}"
        }
        
        val timeInfo = lastActivity?.let {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            "\nÚltima actividad: ${formatter.format(Date(it))}"
        } ?: ""
        
        // Intent para abrir la app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Intent para reiniciar el servicio
        val restartIntent = Intent(ACTION_RESTART).apply {
            setPackage(context.packageName)
        }
        val restartPendingIntent = PendingIntent.getBroadcast(
            context, 
            1, 
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Intent para abrir configuración
        val settingsIntent = Intent(ACTION_SETTINGS).apply {
            setPackage(context.packageName)
        }
        val settingsPendingIntent = PendingIntent.getBroadcast(
            context, 
            2, 
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Intent para dismiss
        val dismissIntent = Intent(ACTION_DISMISS).apply {
            setPackage(context.packageName)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, 
            3, 
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Servicio detenido inesperadamente")
            .setContentText(reasonText + timeInfo)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$reasonText$timeInfo\n\nEl monitoreo de notificaciones no está funcionando. " +
                        "Toca 'Reiniciar' para intentar reactivarlo.")
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "Entendido",
                dismissPendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                "Reiniciar",
                restartPendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                "⚙️ Config",
                settingsPendingIntent
            )
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_CRASH, notification)
        
        // Guardar que se mostró la notificación
        val prefs = context.getSharedPreferences("service_crash_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("last_crash_notification", System.currentTimeMillis())
            .putString("last_crash_reason", reason.toString())
            .putInt("crash_notification_count", prefs.getInt("crash_notification_count", 0) + 1)
            .apply()
    }
    
    /**
     * Muestra una notificación de recuperación en progreso
     */
    fun showRecoveryNotification(attemptCount: Int) {
        Log.d(TAG, "Mostrando notificación de recuperación (intento #$attemptCount)")
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Recuperando servicio...")
            .setContentText("Intento #$attemptCount de reiniciar el monitoreo")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_RECOVERY, notification)
    }
    
    /**
     * Muestra notificación de recuperación exitosa
     */
    fun showRecoverySuccessNotification() {
        Log.d(TAG, "Mostrando notificación de recuperación exitosa")
        
        dismissRecoveryNotification()
        dismissCrashNotification()
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Servicio restaurado")
            .setContentText("El monitoreo está funcionando correctamente")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_RECOVERY, notification)
    }
    
    /**
     * Descarta la notificación de crash
     */
    fun dismissCrashNotification() {
        Log.d(TAG, "Descartando notificación de crash")
        notificationManager.cancel(NOTIFICATION_ID_CRASH)
    }
    
    /**
     * Descarta la notificación de recuperación
     */
    fun dismissRecoveryNotification() {
        Log.d(TAG, "Descartando notificación de recuperación")
        notificationManager.cancel(NOTIFICATION_ID_RECOVERY)
    }
    
    /**
     * Descarta todas las notificaciones del servicio
     */
    fun dismissAllNotifications() {
        dismissCrashNotification()
        dismissRecoveryNotification()
    }
    
    /**
     * Verifica si se debe mostrar una notificación de crash
     * (evita spam de notificaciones)
     */
    fun shouldShowCrashNotification(): Boolean {
        val prefs = context.getSharedPreferences("service_crash_prefs", Context.MODE_PRIVATE)
        val lastNotification = prefs.getLong("last_crash_notification", 0)
        val now = System.currentTimeMillis()
        
        // No mostrar más de una notificación cada 5 minutos
        return now - lastNotification > 5 * 60 * 1000L
    }
}
