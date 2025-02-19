package com.dynamictecnologies.notificationmanager.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import java.text.SimpleDateFormat
import java.util.*

class NotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras

        // Información básica
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""

        // Obtener el nombre de la aplicación
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        val appName = packageManager.getApplicationLabel(appInfo).toString()

        // Información adicional que podría estar disponible
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val sender = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
        val groupName = if (isGroup) extras.getString(Notification.EXTRA_SUMMARY_TEXT) else null

        // Crear el timestamp en el formato correcto
        val currentDate = Date(sbn.postTime)

        val notificationInfo = NotificationInfo(
            packageName = packageName,
            appName = appName,
            title = title,
            content = text,
            timestamp = currentDate,
            senderName = sender,
            isGroupMessage = isGroup,
            groupName = groupName,
            isRead = false
        )

        // Aquí implementarías la lógica para guardar en la base de datos
    }

    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(date)
    }
}