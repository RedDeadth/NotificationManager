package com.dynamictecnologies.notificationmanager.worker

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.ServiceNotificationManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests para ServiceHealthCheckWorker.
 * 
 * Verifica el comportamiento correcto del watchdog:
 * - Detección de servicio muerto
 * - Correcto orden: detener servicio -> cancelar notificación verde -> mostrar notificación roja
 * - Actualización de SharedPreferences
 * 
 * Principios aplicados:
 * - AAA Pattern (Arrange-Act-Assert)
 * - Isolated tests con MockK
 * - Given-When-Then naming
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServiceHealthCheckWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPrefsEditor: SharedPreferences.Editor
    private lateinit var notificationManager: NotificationManager
    private lateinit var activityManager: ActivityManager

    @Before
    fun setup() {
        // Mock context and services
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        sharedPrefsEditor = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        activityManager = mockk(relaxed = true)

        // Setup SharedPreferences
        every { context.getSharedPreferences("service_state", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns sharedPrefsEditor
        every { sharedPrefsEditor.putLong(any(), any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.putInt(any(), any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.putBoolean(any(), any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.apply() } just Runs

        // Setup services
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        
        // Mock stopService
        every { context.stopService(any<Intent>()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ===== HEARTBEAT DETECTION TESTS =====

    @Test
    fun `doWork returns success when service should not be running`() {
        // Given: Service is not supposed to be running
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns false

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should return success without any action
        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { context.stopService(any()) }
    }

    @Test
    fun `doWork detects dead service when no heartbeat exists`() {
        // Given: Service should be running but no heartbeat
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns 0L

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should detect dead service and take action
        assertEquals(ListenableWorker.Result.success(), result)
        verify { context.stopService(any<Intent>()) }
        verify { notificationManager.cancel(ServiceNotificationManager.NOTIFICATION_ID_RUNNING) }
    }

    @Test
    fun `doWork detects dead service when heartbeat is stale`() {
        // Given: Service should be running but heartbeat is old (> 15 minutes)
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        val staleHeartbeat = System.currentTimeMillis() - (20 * 60 * 1000L) // 20 minutes ago
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns staleHeartbeat
        
        // Mock: Service not running
        every { activityManager.getRunningServices(any()) } returns emptyList()

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should detect dead service
        assertEquals(ListenableWorker.Result.success(), result)
        verify { context.stopService(any<Intent>()) }
    }

    @Test
    fun `doWork returns success when service is healthy and heartbeat is recent`() {
        // Given: Service should be running and heartbeat is very recent (within safe range)
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        // Heartbeat: 1 minute ago (well within the 8-15 min timeout)
        val recentHeartbeat = System.currentTimeMillis() - (1 * 60 * 1000L)
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns recentHeartbeat
        
        // Note: We don't mock getRunningServices because the heartbeat check passes first
        // and it's a valid healthy state indicator

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should return success
        // The heartbeat is within range, so even without service check, it's considered healthy
        assertEquals(ListenableWorker.Result.success(), result)
    }

    // ===== HANDLE DEAD SERVICE TESTS =====

    @Test
    fun `handleDeadService stops foreground service first`() {
        // Given: Service is dead
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns 0L
        every { sharedPreferences.getInt("death_count", 0) } returns 0

        // When: Worker executes
        val worker = createWorker()
        worker.doWork()

        // Then: Should call stopService BEFORE showing notification
        verifyOrder {
            context.stopService(any<Intent>())
            notificationManager.cancel(ServiceNotificationManager.NOTIFICATION_ID_RUNNING)
        }
    }

    @Test
    fun `handleDeadService cancels running notification`() {
        // Given: Service is dead
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns 0L
        every { sharedPreferences.getInt("death_count", 0) } returns 0

        // When: Worker executes
        val worker = createWorker()
        worker.doWork()

        // Then: Should cancel NOTIFICATION_ID_RUNNING
        verify { notificationManager.cancel(ServiceNotificationManager.NOTIFICATION_ID_RUNNING) }
    }

    @Test
    fun `handleDeadService updates shared preferences correctly`() {
        // Given: Service is dead
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns 0L
        every { sharedPreferences.getInt("death_count", 0) } returns 2

        // When: Worker executes
        val worker = createWorker()
        worker.doWork()

        // Then: Should update all relevant preferences
        verify { sharedPrefsEditor.putLong("last_death_detected", any()) }
        verify { sharedPrefsEditor.putInt("death_count", 3) } // incremented
        verify { sharedPrefsEditor.putBoolean("service_should_be_running", false) }
        verify { sharedPrefsEditor.apply() }
    }

    @Test
    fun `handleDeadService increments death count`() {
        // Given: Service has died before
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns 0L
        every { sharedPreferences.getInt("death_count", 0) } returns 5

        // When: Worker executes
        val worker = createWorker()
        worker.doWork()

        // Then: death_count should be incremented to 6
        verify { sharedPrefsEditor.putInt("death_count", 6) }
    }

    // ===== ERROR HANDLING TESTS =====

    @Test
    fun `doWork returns failure on exception`() {
        // Given: Context throws exception
        every { context.getSharedPreferences(any(), any()) } throws RuntimeException("Test error")

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should return failure
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `handleDeadService handles stopService exception gracefully`() {
        // Given: stopService throws exception
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns 0L
        every { sharedPreferences.getInt("death_count", 0) } returns 0
        every { context.stopService(any<Intent>()) } throws SecurityException("No permission")

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should still continue and return success
        assertEquals(ListenableWorker.Result.success(), result)
        // Should still try to cancel notification
        verify { notificationManager.cancel(ServiceNotificationManager.NOTIFICATION_ID_RUNNING) }
    }

    // ===== HELPER METHODS =====

    private fun createWorker(): ServiceHealthCheckWorker {
        return ServiceHealthCheckWorker(context, workerParams)
    }
}
