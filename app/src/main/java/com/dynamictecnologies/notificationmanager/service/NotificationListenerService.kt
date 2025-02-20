package com.dynamictecnologies.notificationmanager.service

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class NotificationListenerService : NotificationListenerService() {
    private val TAG = "NotificationListener"
    private lateinit var repository: NotificationRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val recentNotifications = mutableSetOf<String>()
    private val CACHE_TIMEOUT = 500L

    override fun onCreate() {
        super.onCreate()
        val database = NotificationDatabase.getDatabase(applicationContext)
        repository = NotificationRepository(database.notificationDao())
        Log.d(TAG, "NotificationListenerService creado")
        startForegroundService()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        serviceScope.launch {
            try {
                val packageName = sbn.packageName
                val notification = sbn.notification
                val extras = notification.extras

                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
                val timestamp = sbn.postTime

                // Crear una clave única para la notificación
                val notificationKey = "$packageName:$title:$text:$timestamp"

                // Verificar si es una notificación duplicada
                if (isDuplicate(notificationKey)) {
                    Log.d(TAG, "Ignorando notificación duplicada: $title")
                    return@launch
                }

                // Si no es duplicada, procesarla
                Log.d(TAG, "Nueva notificación recibida:")
                Log.d(TAG, "App: $packageName")
                Log.d(TAG, "Título: $title")
                Log.d(TAG, "Contenido: $text")

                // Ignorar notificaciones de resumen
                if (text.contains("new message", ignoreCase = true) ||
                    text.contains("new messages", ignoreCase = true)) {
                    Log.d(TAG, "Ignorando notificación de resumen")
                    return@launch
                }

                val appInfo = withContext(Dispatchers.IO) {
                    packageManager.getApplicationInfo(packageName, 0)
                }
                val appName = packageManager.getApplicationLabel(appInfo).toString()

                val notificationInfo = NotificationInfo(
                    id = 0,
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    content = text,
                    timestamp = Date(timestamp)
                )

                repository.insertNotification(notificationInfo)
                Log.d(TAG, "✓ Notificación guardada en la base de datos")

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    private fun isDuplicate(key: String): Boolean {
        val currentTime = System.currentTimeMillis()

        recentNotifications.removeIf { it.split("|")[1].toLong() < currentTime - CACHE_TIMEOUT }

        val keyWithTimestamp = "$key|$currentTime"

        val isDuplicate = recentNotifications.any {
            it.split("|")[0] == key
        }

        if (!isDuplicate) {
            recentNotifications.add(keyWithTimestamp)
        }

        return isDuplicate
    }


    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService conectado")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListenerService desconectado")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "NotificationListenerService destruido")
    }
    private fun startForegroundService() {
        val serviceIntent = Intent(this, NotificationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}