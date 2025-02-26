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
import com.dynamictecnologies.notificationmanager.service.FirebaseService
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class NotificationListenerService : NotificationListenerService() {
    private val TAG = "NotificationListener"
    private lateinit var repository: NotificationRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Usar ConcurrentHashMap para thread-safety
    private val recentNotifications = ConcurrentHashMap<String, Long>()
    private val CACHE_TIMEOUT = 3000L // 3 segundos de timeout

    // Patrones de notificaciones de resumen
    private val summaryPatterns = listOf(
        "\\d+ (?:nuevos? )?mensajes?(?: de \\d+ chats?)?".toRegex(),
        "new messages?".toRegex(),
        "messages from".toRegex(),
        "\\d+ chats?".toRegex()
    )

    override fun onCreate() {
        super.onCreate()
        val database = NotificationDatabase.getDatabase(applicationContext)
        val firebaseService = FirebaseService()
        repository = NotificationRepository(
            notificationDao = database.notificationDao(),
            firebaseService = firebaseService,
            context = applicationContext
        )
        Log.d(TAG, "NotificationListenerService creado")
        startForegroundService()
        startCacheCleaning()
    }

    private fun startCacheCleaning() {
        serviceScope.launch {
            while (isActive) {
                cleanOldCache()
                delay(CACHE_TIMEOUT)
            }
        }
    }

    private fun cleanOldCache() {
        val currentTime = System.currentTimeMillis()
        recentNotifications.entries.removeIf {
            currentTime - it.value > CACHE_TIMEOUT
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        serviceScope.launch {
            try {
                val notification = sbn.notification
                val extras = notification.extras

                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
                val timestamp = sbn.postTime

                // Verificar si es una notificación de resumen
                if (isSummaryNotification(notification, text)) {
                    Log.d(TAG, "Ignorando notificación de resumen: $text")
                    return@launch
                }

                // Crear clave única
                val notificationKey = createUniqueKey(sbn.packageName, title, text, timestamp)

                // Verificar duplicados
                if (isDuplicate(notificationKey)) {
                    Log.d(TAG, "Ignorando notificación duplicada: $title")
                    return@launch
                }

                // Registrar la notificación en el cache
                recentNotifications[notificationKey] = System.currentTimeMillis()

                Log.d(TAG, "Nueva notificación recibida:")
                Log.d(TAG, "App: ${sbn.packageName}")
                Log.d(TAG, "Título: $title")
                Log.d(TAG, "Contenido: $text")

                withContext(Dispatchers.IO) {
                    val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()

                    val notificationInfo = NotificationInfo(
                        packageName = sbn.packageName,
                        appName = appName,
                        title = title,
                        content = text,
                        timestamp = Date(timestamp),
                        uniqueId = notificationKey
                    )

                    repository.insertNotification(notificationInfo)
                    Log.d(TAG, "✓ Notificación guardada en la base de datos")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun isSummaryNotification(notification: Notification, text: String): Boolean {
        // Verificar si es una notificación de grupo
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return true
        }

        // Verificar patrones de texto conocidos
        return summaryPatterns.any { pattern ->
            pattern.matches(text)
        }
    }

    private fun createUniqueKey(packageName: String, title: String, text: String, timestamp: Long): String {
        // Redondear timestamp a segundos para mayor tolerancia
        return "$packageName:$title:$text:${timestamp / 1000}"
    }

    private fun isDuplicate(key: String): Boolean {
        val currentTime = System.currentTimeMillis()
        return recentNotifications[key]?.let { lastTime ->
            currentTime - lastTime < CACHE_TIMEOUT
        } ?: false
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