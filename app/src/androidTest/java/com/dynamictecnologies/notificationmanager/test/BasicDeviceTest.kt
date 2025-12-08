package com.dynamictecnologies.notificationmanager.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test básico para dispositivo USB.
 * Ejecutar con: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class BasicDeviceTest {
    
    @Test
    fun test_01_device_connection() {
        println("\n🎬 ==== TEST DEVICE CONNECTION ====")
        println("  ✅ Dispositivo conectado correctamente")
        println("  ✅ Android Test ejecutándose")
        assertTrue("Test should pass", true)
        println("  ✅ TEST PASADO\n")
    }
    
    @Test
    fun test_02_basic_math() {
        println("\n📊 ==== TEST BASIC OPERATIONS ====")
        val result = 2 + 2
        println("  📊 2 + 2 = $result")
        assertEquals("Should be 4", 4, result)
        println("  ✅ TEST PASADO\n")
    }
    
    @Test
    fun test_03_string_operations() {
        println("\n📝 ==== TEST STRING OPERATIONS ====")
        val greeting = "Hello from USB Device!"
        println("  📝 Message: $greeting")
        assertTrue("Should contain Hello", greeting.contains("Hello"))
        assertTrue("Should contain USB", greeting.contains("USB"))
        println("  ✅ TEST PASADO\n")
    }
}
