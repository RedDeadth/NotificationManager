package com.dynamictecnologies.notificationmanager.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests unitarios para ServiceNotificationManager.
 * 
 * Verifica:
 * - Creación de canales de notificación
 * - Notificación RUNNING con botón DETENER
 * - Notificación STOPPED con botones Reiniciar/Entendido
 * - Ocultamiento de notificaciones
 */
@RunWith(RobolectricTestRunner::class)
class ServiceNotificationManagerTest {
    
    private lateinit var context: Context
    private lateinit var manager: ServiceNotificationManager
    private lateinit var notificationManager: NotificationManager
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = ServiceNotificationManager(context)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    @Test
    fun test_01_notificationChannelsCreated() {
        // Verificar que canales fueron creados
        val runningChannel = notificationManager.getNotificationChannel("service_running_channel")
        val stoppedChannel = notificationManager.getNotificationChannel("service_stopped_channel")
        
        assertNotNull("Canal RUNNING debe existir", runningChannel)
        assertNotNull("Canal STOPPED debe existir", stoppedChannel)
    }
    
    @Test
    fun test_02_runningChannelProperties() {
        val channel = notificationManager.getNotificationChannel("service_running_channel")
        
        assertNotNull(channel)
        assertEquals("Servicio Activo", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertFalse("Badge deshabilitado", channel.canShowBadge())
    }
    
    @Test
    fun test_03_stoppedChannelProperties() {
        val channel = notificationManager.getNotificationChannel("service_stopped_channel")
        
        assertNotNull(channel)
        assertEquals("Servicio Detenido", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue("Badge habilitado", channel.canShowBadge())
    }
    
    @Test
    fun test_04_showRunningNotification() {
        val notification = manager.showRunningNotification()
        
        assertNotNull("Debe retornar notificación", notification)
        
        // Verificar que notificación RUNNING está activa
        val activeNotifications = notificationManager.activeNotifications
        val runningNotif = activeNotifications.find { 
            it.id == ServiceNotificationManager.NOTIFICATION_ID_RUNNING 
        }
        
        assertNotNull("Notificación RUNNING debe estar activa", runningNotif)
    }
    
    @Test
    fun test_05_runningNotificationIsOngoing() {
        val notification = manager.showRunningNotification()
        
        assertTrue(
            "Notificación RUNNING debe ser ongoing (no swipeable)",
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        )
    }
    
    @Test
    fun test_06_runningNotificationHasStopAction() {
        val notification = manager.showRunningNotification()
        
        assertNotNull("Debe tener acciones", notification.actions)
        assertEquals("Debe tener 1 acción (DETENER)", 1, notification.actions.size)
        
        val action = notification.actions[0]
        assertTrue(
            "Acción debe contener 'DETENER'",
            action.title.toString().contains("DETENER")
        )
    }
    
    @Test
    fun test_07_showStoppedNotification() {
        manager.showStoppedNotification()
        
        // Verificar que notificación STOPPED está activa
        val activeNotifications = notificationManager.activeNotifications
        val stoppedNotif = activeNotifications.find { 
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED 
        }
        
        assertNotNull("Notificación STOPPED debe estar activa", stoppedNotif)
    }
    
    @Test
    fun test_08_stoppedNotificationHasTwoActions() {
        manager.showStoppedNotification()
        
        val activeNotifications = notificationManager.activeNotifications
        val stoppedNotif = activeNotifications.find { 
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED 
        }
        
        assertNotNull(stoppedNotif)
        assertNotNull("Debe tener acciones", stoppedNotif?.notification?.actions)
        assertEquals(
            "Debe tener 2 acciones (Reiniciar/Entendido)",
            2,
            stoppedNotif?.notification?.actions?.size
        )
    }
    
    @Test
    fun test_09_stoppedNotificationActionsText() {
        manager.showStoppedNotification()
        
        val activeNotifications = notificationManager.activeNotifications
        val stoppedNotif = activeNotifications.find { 
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED 
        }
        
        val actions = stoppedNotif?.notification?.actions
        assertNotNull(actions)
        
        val actionTitles = actions!!.map { it.title.toString() }
        
        assertTrue(
            "Debe contener acción 'Reiniciar'",
            actionTitles.any { it.contains("Reiniciar") }
        )
        assertTrue(
            "Debe contener acción 'Entendido'",
            actionTitles.any { it.contains("Entendido") }
        )
    }
    
    @Test
    fun test_10_showRunningHidesStoppedNotification() {
        // Mostrar STOPPED primero
        manager.showStoppedNotification()
        
        // Verificar que STOPPED está activa
        var stoppedActive = notificationManager.activeNotifications.any { 
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED 
        }
        assertTrue("STOPPED debe estar activa", stoppedActive)
        
        // Mostrar RUNNING
        manager.showRunningNotification()
        
        // Verificar que STOPPED fue cancelada
        stoppedActive = notificationManager.activeNotifications.any { 
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED 
        }
        assertFalse("STOPPED debe estar cancelada", stoppedActive)
    }
    
    @Test
    fun test_11_showStoppedHidesRunningNotification() {
        // Mostrar RUNNING primero
        manager.showRunningNotification()
        
        // Verificar que RUNNING está activa
        var runningActive = notificationManager.activeNotifications.any { 
            it.id == ServiceNotificationManager.NOTIFICATION_ID_RUNNING 
        }
        assertTrue("RUNNING debe estar activa", runningActive)
        
        // Mostrar STOPPED
        manager.showStoppedNotification()
        
        // Verificar que RUNNING fue cancelada
        runningActive = notificationManager.activeNotifications.any { 
            it.id == ServiceNotificationManager.NOTIFICATION_ID_RUNNING 
        }
        assertFalse("RUNNING debe estar cancelada", runningActive)
    }
    
    @Test
    fun test_12_hideAllNotifications() {
        // Mostrar ambas notificaciones
        manager.showRunningNotification()
        manager.showStoppedNotification()
        
        // Verificar que al menos una está activa
        assertTrue(
            "Debe haber notificaciones activas",
            notificationManager.activeNotifications.isNotEmpty()
        )
        
        // Ocultar todas
        manager.hideAllNotifications()
        
        // Verificar que ninguna de nuestras notificaciones está activa
        val ourNotifications = notificationManager.activeNotifications.filter {
            it.id == ServiceNotificationManager.NOTIFICATION_ID_RUNNING ||
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED
        }
        
        assertTrue(
            "Todas las notificaciones deben estar ocultas",
            ourNotifications.isEmpty()
        )
    }
    
    @Test
    fun test_13_notificationIDsAreUnique() {
        // Verificar que los IDs son diferentes
        assertNotEquals(
            "IDs de notificaciones deben ser diferentes",
            ServiceNotificationManager.NOTIFICATION_ID_RUNNING,
            ServiceNotificationManager.NOTIFICATION_ID_STOPPED
        )
    }
    
    @Test
    fun test_14_runningNotificationContent() {
        val notification = manager.showRunningNotification()
        
        // Verificar contenido de la notificación
        val extras = notification.extras
        
        assertTrue(
            "Título debe contener 'Gestor de Notificaciones'",
            extras.getString(Notification.EXTRA_TITLE)?.contains("Gestor de Notificaciones") == true
        )
        
        assertTrue(
            "Texto debe contener 'segundo plano'",
            extras.getString(Notification.EXTRA_TEXT)?.contains("segundo plano") == true
        )
    }
    
    @Test
    fun test_15_stoppedNotificationContent() {
        manager.showStoppedNotification()
        
        val activeNotifications = notificationManager.activeNotifications
        val stoppedNotif = activeNotifications.find { 
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED 
        }
        
        val extras = stoppedNotif?.notification?.extras
        assertNotNull(extras)
        
        assertTrue(
            "Título debe contener 'Detenido'",
            extras?.getString(Notification.EXTRA_TITLE)?.contains("Detenido") == true
        )
    }
}
