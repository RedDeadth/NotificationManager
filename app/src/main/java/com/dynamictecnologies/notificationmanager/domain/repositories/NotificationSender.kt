package com.dynamictecnologies.notificationmanager.domain.repositories

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo

/**
 * Interfaz para envío de notificaciones a dispositivos.
 * 
 * Sigue el principio de Inversión de Dependencias (DIP):
 * La capa Domain define la interfaz, la capa Data la implementa.
 * 
 * Esto permite:
 * - Cambiar la implementación (MQTT, Firebase, etc.) sin tocar los Use Cases
 * - Facilitar testing con mocks
 */
interface NotificationSender {
    
    /**
     * Establece el usuario actual para incluir en las notificaciones.
     * 
     * @param userId ID del usuario
     * @param username Nombre de usuario (opcional)
     */
    fun setCurrentUser(userId: String, username: String?)
    
    /**
     * Envía una notificación a un dispositivo específico.
     * 
     * @param deviceId ID del dispositivo destino
     * @param notification Datos de la notificación
     * @return Result<Unit> Success si se envía, Failure con excepción si falla
     */
    suspend fun sendNotification(deviceId: String, notification: NotificationInfo): Result<Unit>
    
    /**
     * Envía una notificación a un topic MQTT específico.
     * 
     * @param topic Topic MQTT destino
     * @param notification Datos de la notificación
     * @return Result<Unit> Success si se envía, Failure con excepción si falla
     */
    suspend fun sendNotificationToTopic(topic: String, notification: NotificationInfo): Result<Unit>
    
    /**
     * Envía una notificación general.
     * 
     * @param title Título de la notificación
     * @param content Contenido de la notificación
     * @return Result<Unit> Success si se envía, Failure con excepción si falla
     */
    suspend fun sendGeneralNotification(title: String, content: String): Result<Unit>
}
