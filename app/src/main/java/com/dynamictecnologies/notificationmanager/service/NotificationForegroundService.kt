package com.dynamictecnologies.notificationmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dynamictecnologies.notificationmanager.R

class NotificationForegroundService : Service() {
    private val TAG = "NotifForegroundService"
    private val CHANNEL_ID = "notification_manager_service"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio en primer plano creado")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio en primer plano iniciado")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notification Manager Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activo el servicio de monitoreo de notificaciones"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Canal de notificación creado")
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Notification Manager")
            .setContentText("Monitoreando notificaciones...")
            .setSmallIcon(R.drawable.ic_notification) // Usamos R.drawable
            .setOngoing(true)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    priority = NotificationCompat.PRIORITY_LOW
                }
            }
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio en primer plano destruido")
    }
}