package com.dynamictecnologies.notificationmanager.data.repository

import android.content.SharedPreferences
import com.dynamictecnologies.notificationmanager.domain.repositories.PreferencesRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para PreferencesRepositoryImpl.
 * 
 * Verifica que el repository correctamente:
 * - Guarda y recupera package names
 * - Limpia preferencias
 * - Maneja casos edge
 */
class PreferencesRepositoryImplTest {

    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var repository: PreferencesRepository

    @Before
    fun setup() {
        mockEditor = mockk(relaxed = true)
        mockSharedPreferences = mockk {
            every { edit() } returns mockEditor
        }
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit
        
        repository = PreferencesRepositoryImpl(mockSharedPreferences)
    }

    @Test
    fun `saveSelectedApp stores package name correctly`() {
        // Given
        val packageName = "com.example.app"

        // When
        repository.saveSelectedApp(packageName)

        // Then
        verify { mockEditor.putString("last_selected_app", packageName) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `getSelectedApp returns stored package name`() {
        // Given
        val expectedPackage = "com.test.app"
        every { mockSharedPreferences.getString("last_selected_app", null) } returns expectedPackage

        // When
        val result = repository.getSelectedApp()

        // Then
        assertEquals("Should return stored package name", expectedPackage, result)
        verify { mockSharedPreferences.getString("last_selected_app", null) }
    }

    @Test
    fun `getSelectedApp returns null when no app selected`() {
        // Given
        every { mockSharedPreferences.getString("last_selected_app", null) } returns null

        // When
        val result = repository.getSelectedApp()

        // Then
        assertNull("Should return null when no app selected", result)
    }

    @Test
    fun `clearSelectedApp removes stored preference`() {
        // When
        repository.clearSelectedApp()

        // Then
        verify { mockEditor.remove("last_selected_app") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `saveSelectedApp can save empty string`() {
        // Given
        val emptyPackage = ""

        // When
        repository.saveSelectedApp(emptyPackage)

        // Then
        verify { mockEditor.putString("last_selected_app", emptyPackage) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `saveSelectedApp overwrites previous value`() {
        // Given
        val firstPackage = "com.app1"
        val secondPackage = "com.app2"

        // When
        repository.saveSelectedApp(firstPackage)
        repository.saveSelectedApp(secondPackage)

        // Then
        verify { mockEditor.putString("last_selected_app", firstPackage) }
        verify { mockEditor.putString("last_selected_app", secondPackage) }
        verify(exactly = 2) { mockEditor.apply() }
    }

    @Test
    fun `repository handles SharedPreferences exceptions gracefully`() {
        // Given
        every { mockSharedPreferences.edit() } throws RuntimeException("Preferences error")

        // When/Then - should not throw
        try {
            repository.saveSelectedApp("com.test.app")
            // If it doesn't throw, the test passes
        } catch (e: Exception) {
            fail("Should handle exceptions gracefully")
        }
    }
}
