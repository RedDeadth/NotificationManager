package com.dynamictecnologies.notificationmanager.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dynamictecnologies.notificationmanager.MainActivity
import com.dynamictecnologies.notificationmanager.R

/**
 * Servicio de primer plano que mantiene la app corriendo en segundo plano.
 * 
 * Cumple con Android 12+ (API 31+) requirements:
 * - Notificación OBLIGATORIA mientras esté corriendo
 * - Foreground service type especificado
 * - WorkManager para tareas diferidas
 * - Optimización de batería considerada
 * 
 * Comportamiento:
 * - Inicia automáticamente al boot (si usuario lo permitió)
 * - Continúa ejecutándose incluso si usuario cierra la app
 * - Se detiene SOLO si:
 *   1. Usuario detiene manualmente desde notificación
 *   2. Sistema mata por falta de recursos extrema
 *   3. Permiso de notificaciones es revocado
 * 
 * Android 12+ Changes:
 * - Usando ForegroundServiceType.dataSync
 * - PendingIntent con FLAG_IMMUTABLE obligatorio
 * - Notification must be posted within 5 seconds
 */
class BackgroundMonitoringService : Service() {
    
    companion object {
        private const val TAG = "BackgroundService"
        private const val CHANNEL_ID = "background_monitoring_channel"
        private const val NOTIFICATION_ID = 101
        private const val ACTION_STOP = "com.dynamictecnologies.notificationmanager.STOP_SERVICE"
        
        /**
         * Inicia el servicio de forma compatible con todas las versiones de Android.
         */
        fun start(context: Context) {
            val intent = Intent(context, BackgroundMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Detiene el servicio.
         */
        fun stop(context: Context) {
            val intent = Intent(context, BackgroundMonitoringService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Crear canal de notificación ANTES de startForeground
        createNotificationChannel()
        
        // CRÍTICO: Debe llamarse dentro de 5 segundos en Android 12+
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        // START_STICKY: El sistema reiniciará el servicio si es matado
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Monitoreo en Segundo Plano"
            val descriptionText = "Mantiene la app funcionando para capturar notificaciones"
            val importance = NotificationManager.IMPORTANCE_LOW // No molesta al usuario
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false) // No mostrar badge
                enableLights(false) // No luces
                enableVibration(false) // No vibración
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        // Intent para abrir la app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, pendingIntentFlags
        )
        
        // Intent para detener el servicio
        val stopIntent = Intent(this, BackgroundMonitoringService::class.java).apply {
            action = ACTION_STOP
        }
        
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, pendingIntentFlags
        )
        
        // Construir notificación
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📱 Gestor de Notificaciones Activo")
            .setContentText("Monitoreando notificaciones en segundo plano")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Baja prioridad, no molesta
            .setOngoing(true) // No se puede swipear
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "Detener",
                stopPendingIntent
            )
        
        // Android 12+ requiere ForegroundServiceType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        
        return builder.build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Aquí podrías mostrar ServiceRecoveryDialog si fue detenido inesperadamente
        // Pero solo si NO fue el usuario quien lo detuvo
    }
}
