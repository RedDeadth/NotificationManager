package com.dynamictecnologies.notificationmanager.service

import android.app.Notification
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

    override fun onCreate() {
        super.onCreate()
        val database = NotificationDatabase.getDatabase(applicationContext)
        repository = NotificationRepository(database.notificationDao())
        Log.d(TAG, "NotificationListenerService creado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // Ignorar notificaciones propias

        serviceScope.launch {
            try {
                val packageName = sbn.packageName
                val notification = sbn.notification
                val extras = notification.extras

                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getString(Notification.EXTRA_TEXT) ?: ""

                val appInfo = withContext(Dispatchers.IO) {
                    packageManager.getApplicationInfo(packageName, 0)
                }
                val appName = packageManager.getApplicationLabel(appInfo).toString()

                Log.d(TAG, "Nueva notificación recibida:")
                Log.d(TAG, "App: $appName ($packageName)")
                Log.d(TAG, "Título: $title")
                Log.d(TAG, "Contenido: $text")

                val notificationInfo = NotificationInfo(
                    id = 0, // Room generará el ID
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    content = text,
                    timestamp = Date()
                )

                repository.insertNotification(notificationInfo)
                Log.d(TAG, "✓ Notificación guardada en la base de datos")
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación: ${e.message}")
                e.printStackTrace()
            }
        }
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
}