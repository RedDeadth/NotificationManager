package com.dynamictecnologies.notificationmanager.service.util

import android.content.Context
import android.content.SharedPreferences
import com.dynamictecnologies.notificationmanager.service.ServiceStateManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests unitarios para ServiceDeathDetector.
 * 
 * Verifica:
 * - Detección correcta de muerte del servicio
 * - Manejo correcto de estados
 * - No mostrar notificación cuando no aplica
 * 
 * Principios aplicados:
 * - AAA Pattern (Arrange-Act-Assert)
 * - Given-When-Then naming
 * - Isolated tests con MockK
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServiceDeathDetectorTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPrefsEditor: SharedPreferences.Editor
    private lateinit var statePrefs: SharedPreferences
    private lateinit var statePrefsEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        sharedPrefsEditor = mockk(relaxed = true)
        statePrefs = mockk(relaxed = true)
        statePrefsEditor = mockk(relaxed = true)

        // Setup SharedPreferences para service_state
        every { context.getSharedPreferences("service_state", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns sharedPrefsEditor
        every { sharedPrefsEditor.putBoolean(any(), any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.putLong(any(), any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.putInt(any(), any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.apply() } just Runs

        // Setup SharedPreferences para service_state_prefs (ServiceStateManager)
        every { context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE) } returns statePrefs
        every { statePrefs.edit() } returns statePrefsEditor
        every { statePrefs.getString("current_state", any()) } returns "RUNNING"
        every { statePrefsEditor.putString(any(), any()) } returns statePrefsEditor
        every { statePrefsEditor.putLong(any(), any()) } returns statePrefsEditor
        every { statePrefsEditor.putBoolean(any(), any()) } returns statePrefsEditor
        every { statePrefsEditor.putInt(any(), any()) } returns statePrefsEditor
        every { statePrefsEditor.apply() } just Runs
        
        // Setup SharedPreferences para service_death_detector
        every { context.getSharedPreferences("service_death_detector", Context.MODE_PRIVATE) } returns sharedPreferences
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ===== wasServiceKilledUnexpectedly TESTS =====

    @Test
    fun `wasServiceKilledUnexpectedly returns false when state is DISABLED`() {
        // Given: Estado DISABLED (usuario desactivó intencionalmente)
        every { statePrefs.getString("current_state", any()) } returns "DISABLED"

        // When
        val result = ServiceDeathDetector.wasServiceKilledUnexpectedly(context)

        // Then
        assertFalse("No debe detectar muerte si estado es DISABLED", result)
    }

    @Test
    fun `wasServiceKilledUnexpectedly returns false when service should not be running`() {
        // Given: Servicio no debería estar corriendo
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns false

        // When
        val result = ServiceDeathDetector.wasServiceKilledUnexpectedly(context)

        // Then
        assertFalse("No debe detectar muerte si servicio no debería correr", result)
    }

    @Test
    fun `wasServiceKilledUnexpectedly returns true when no heartbeat exists`() {
        // Given: Estado RUNNING, debería correr, pero nunca hubo heartbeat
        every { statePrefs.getString("current_state", any()) } returns "RUNNING"
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns 0L

        // When
        val result = ServiceDeathDetector.wasServiceKilledUnexpectedly(context)

        // Then
        assertTrue("Debe detectar muerte si nunca hubo heartbeat", result)
    }

    @Test
    fun `wasServiceKilledUnexpectedly returns true when heartbeat is stale`() {
        // Given: Heartbeat más viejo que el timeout (15+ minutos)
        every { statePrefs.getString("current_state", any()) } returns "RUNNING"
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        val staleHeartbeat = System.currentTimeMillis() - (20 * 60 * 1000L) // 20 min ago
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns staleHeartbeat

        // When
        val result = ServiceDeathDetector.wasServiceKilledUnexpectedly(context)

        // Then
        assertTrue("Debe detectar muerte si heartbeat es muy viejo", result)
    }

    @Test
    fun `wasServiceKilledUnexpectedly returns false when heartbeat is recent`() {
        // Given: Heartbeat reciente (menos de 15 minutos)
        every { statePrefs.getString("current_state", any()) } returns "RUNNING"
        every { sharedPreferences.getBoolean("service_should_be_running", false) } returns true
        val recentHeartbeat = System.currentTimeMillis() - (5 * 60 * 1000L) // 5 min ago
        every { sharedPreferences.getLong("service_last_heartbeat", 0) } returns recentHeartbeat

        // When
        val result = ServiceDeathDetector.wasServiceKilledUnexpectedly(context)

        // Then
        assertFalse("No debe detectar muerte si heartbeat es reciente", result)
    }

    // ===== markServiceAsRunning TESTS =====

    @Test
    fun `markServiceAsRunning saves state correctly`() {
        // When
        ServiceDeathDetector.markServiceAsRunning(context)

        // Then
        verify { sharedPrefsEditor.putBoolean("last_known_running_state", true) }
        verify { sharedPrefsEditor.putLong("last_start_time", any()) }
        verify { sharedPrefsEditor.apply() }
    }
}
