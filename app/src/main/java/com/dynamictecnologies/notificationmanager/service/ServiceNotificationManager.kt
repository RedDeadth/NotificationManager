package com.dynamictecnologies.notificationmanager.service

import android.app.*
import android.content.Context
import android.content.Intent
    import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dynamictecnologies.notificationmanager.MainActivity
import com.dynamictecnologies.notificationmanager.R

/**
 * Gestor de notificaciones dinámicas del servicio.
 * 
 * Maneja 2 tipos de notificaciones:
 * 1. RUNNING: "Corriendo en segundo plano [DETENER]"
 * 2. STOPPED: "Servicio Detenido [Reiniciar] [Entendido]"
 * 
 * Las notificaciones cambian dinámicamente según el estado del servicio.
 */
class ServiceNotificationManager(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID_RUNNING = "service_running_channel"
        private const val CHANNEL_ID_STOPPED = "service_stopped_channel"
        const val NOTIFICATION_ID_RUNNING = 100
        const val NOTIFICATION_ID_STOPPED = 200
    }
    
    private val notificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Muestra la notificación de servicio CORRIENDO.
     * Incluye botón DETENER.
     */
    fun showRunningNotification(): Notification {
        val notification = createRunningNotification()
        notificationManager.notify(NOTIFICATION_ID_RUNNING, notification)
        
        // Ocultar notificación de stopped si existe
        notificationManager.cancel(NOTIFICATION_ID_STOPPED)
        
        return notification
    }
    
    /**
     * Muestra la notificación de servicio DETENIDO.
     * Incluye botones REINICIAR y ENTENDIDO.
     */
    fun showStoppedNotification() {
        val notification = createStoppedNotification()
        notificationManager.notify(NOTIFICATION_ID_STOPPED, notification)
        
        // Ocultar notificación de running
        notificationManager.cancel(NOTIFICATION_ID_RUNNING)
    }
    
    /**
     * Oculta todas las notificaciones del servicio.
     */
    fun hideAllNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_RUNNING)
        notificationManager.cancel(NOTIFICATION_ID_STOPPED)
    }
    
    /**
     * Crea la notificación de servicio CORRIENDO.
     */
    private fun createRunningNotification(): Notification {
        // Intent para abrir la app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Intent para DETENER
        val stopIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_RUNNING)
            .setContentTitle("Gestor de Notificaciones")
            .setContentText("Corriendo en segundo plano")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true) // No se puede swipear
            .setContentIntent(openPendingIntent)
            .setColor(Color.parseColor("#06402B")) // Color verde oscuro personalizado para toda la notificación
            .setColorized(true) // Hace que el color cubra toda la notificación
            .addAction(
                R.drawable.ic_notification,
                "DETENER",
                stopPendingIntent
            )
        
        // Android 12+ foreground service behavior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        
        return builder.build()
    }
    
    /**
     * Crea la notificación de servicio DETENIDO.
     */
    private fun createStoppedNotification(): Notification {
        // Intent para REINICIAR
        val restartIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_RESTART_SERVICE
        }
        val restartPendingIntent = PendingIntent.getBroadcast(
            context, 2, restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Intent para ENTENDIDO
        val acknowledgeIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_ACKNOWLEDGE
        }
        val acknowledgePendingIntent = PendingIntent.getBroadcast(
            context, 3, acknowledgeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID_STOPPED)
            .setContentTitle("Servicio Detenido")
            .setContentText("El servicio se detuvo inesperadamente")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("El servicio de notificaciones se detuvo.\n\n" +
                        "Presiona Reiniciar para continuar o Entendido para desactivar."))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false) // Se puede swipear
            .setAutoCancel(false)
            .setColor(Color.RED) // Color rojo para toda la notificación
            .setColorized(true) // Hace que el color cubra toda la notificación
            .addAction(
                R.drawable.ic_notification,
                "Reiniciar",
                restartPendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                "Entendido",
                acknowledgePendingIntent
            )
            .build()
    }
    
    /**
     * Crea los canales de notificación para Android 8.0+.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para notificación RUNNING
            val runningChannel = NotificationChannel(
                CHANNEL_ID_RUNNING,
                "Servicio Activo",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificación cuando el servicio está corriendo"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            // Canal para notificación STOPPED (mayor importancia)
            val stoppedChannel = NotificationChannel(
                CHANNEL_ID_STOPPED,
                "Servicio Detenido",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerta cuando el servicio se detiene inesperadamente"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannel(runningChannel)
            notificationManager.createNotificationChannel(stoppedChannel)
        }
    }
}
