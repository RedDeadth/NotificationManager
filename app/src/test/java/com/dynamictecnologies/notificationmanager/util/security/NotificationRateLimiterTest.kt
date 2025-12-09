package com.dynamictecnologies.notificationmanager.util.security

import android.os.Build
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests para NotificationRateLimiter
 * 
 * Principios aplicados:
 * - SOLID: Cada test verifica un comportamiento específico
 * - Clean Code: Nombres descriptivos, AAA pattern (Arrange-Act-Assert)
 * - DRY: Helper methods para setup común
 * 
 * Casos de test:
 * - Rate limiting básico (under/over limit)
 * - Ventana de tiempo
 * - Múltiples identificadores
 * - Edge cases
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class NotificationRateLimiterTest {

    private lateinit var rateLimiter: NotificationRateLimiter
    
    // Test constants siguiendo principio de configurabilidad
    private companion object {
        const val MAX_REQUESTS = 10
        const val WINDOW_MS = 60_000L
        const val TEST_IDENTIFIER = "test-package"
    }
    
    @Before
    fun setup() {
        // Given: Fresh rate limiter con configuración de test
        rateLimiter = NotificationRateLimiter(
            maxRequests = MAX_REQUESTS,
            windowMs = WINDOW_MS
        )
    }

    // ===== BASIC RATE LIMITING =====
    
    @Test
    fun `allowOperation - permite operaciones bajo el límite`() {
        // Given: Rate limiter configurado
        val identifier = TEST_IDENTIFIER
        
        // When & Then: Debe permitir hasta MAX_REQUESTS operaciones
        repeat(MAX_REQUESTS) { count ->
            val allowed = rateLimiter.allowOperation(identifier)
            assertTrue(
                "Operación ${count + 1}/$MAX_REQUESTS debe ser permitida",
                allowed
            )
        }
    }

    @Test
    fun `allowOperation - bloquea operaciones sobre el límite`() {
        // Given: Rate limiter lleno hasta el límite
        repeat(MAX_REQUESTS) {
            rateLimiter.allowOperation(TEST_IDENTIFIER)
        }
        
        // When: Intentar operación extra
        val blockedOperation = rateLimiter.allowOperation(TEST_IDENTIFIER)
        
        // Then: Debe ser bloqueada
        assertFalse(
            "Operación ${MAX_REQUESTS + 1} debe ser bloqueada",
            blockedOperation
        )
    }

    @Test
    fun `allowOperation - permite exactamente en el límite`() {
        // Given: Rate limiter con 9 operaciones
        repeat(MAX_REQUESTS - 1) {
            rateLimiter.allowOperation(TEST_IDENTIFIER)
        }
        
        // When: Operación número 10 (última permitida)
        val tenthAllowed = rateLimiter.allowOperation(TEST_IDENTIFIER)
        
        // Then: Debe ser permitida
        assertTrue("Décima operación debe ser permitida", tenthAllowed)
        
        // When: Operación número 11
        val eleventhBlocked = rateLimiter.allowOperation(TEST_IDENTIFIER)
        
        // Then: Debe ser bloqueada
        assertFalse("Onceava operación debe ser bloqueada", eleventhBlocked)
    }

    // ===== MULTIPLE IDENTIFIERS =====
    
    @Test
    fun `allowOperation - identificadores diferentes tienen límites separados`() {
        // Given: Dos identificadores diferentes
        val identifier1 = "package.one"
        val identifier2 = "package.two"
        
        // When: Llenar limite de identifier1
        repeat(MAX_REQUESTS) {
            rateLimiter.allowOperation(identifier1)
        }
        
        // Then: identifier1 bloqueado, identifier2 permitido
        assertFalse(
            "Identifier1 debe estar bloqueado",
            rateLimiter.allowOperation(identifier1)
        )
        
        assertTrue(
            "Identifier2 debe estar permitido (límite independiente)",
            rateLimiter.allowOperation(identifier2)
        )
    }

    @Test
    fun `allowOperation - múltiples paquetes operan independientemente`() {
        // Given: Tres paquetes diferentes
        val packages = listOf("com.app1", "com.app2", "com.app3")
        
        // When: Cada paquete envía 5 notificaciones
        packages.forEach { pkg ->
            repeat(5) {
                assertTrue(
                    "Paquete $pkg debería permitir operación",
                    rateLimiter.allowOperation(pkg)
                )
            }
        }
        
        // Then: Todos deben tener cuota restante
        packages.forEach { pkg ->
            val currentCount = rateLimiter.getCurrentCount(pkg)
            assertEquals("Paquete $pkg debe tener 5 operaciones", 5, currentCount)
            
            // Aún deben poder hacer 5 más
            assertTrue(
                "Paquete $pkg debe tener cuota restante",
                rateLimiter.allowOperation(pkg)
            )
        }
    }

    // ===== RESET FUNCTIONALITY =====
    
    @Test
    fun `reset - limpia el contador de un identificador`() {
        // Given: Identifier con operaciones registradas
        repeat(5) {
            rateLimiter.allowOperation(TEST_IDENTIFIER)
        }
        
        assertEquals("Debe tener 5 operaciones", 5, rateLimiter.getCurrentCount(TEST_IDENTIFIER))
        
        // When: Reset del identifier
        rateLimiter.reset(TEST_IDENTIFIER)
        
        // Then: Contador debe estar en 0
        assertEquals("Contador debe resetear a 0", 0, rateLimiter.getCurrentCount(TEST_IDENTIFIER))
        
        // And: Debe permitir operaciones nuevamente
        assertTrue(
            "Debe permitir operaciones después del reset",
            rateLimiter.allowOperation(TEST_IDENTIFIER)
        )
    }

    @Test
    fun `clearAll - limpia todos los identificadores`() {
        // Given: Múltiples identificadores con operaciones
        val identifiers = listOf("id1", "id2", "id3")
        identifiers.forEach { id ->
            repeat(5) {
                rateLimiter.allowOperation(id)
            }
        }
        
        // When: ClearAll
        rateLimiter.clearAll()
        
        // Then: Todos los contadores deben estar en 0
        identifiers.forEach { id ->
            assertEquals(
                "Contador de $id debe estar en 0",
                0,
                rateLimiter.getCurrentCount(id)
            )
        }
    }

    // ===== EDGE CASES =====
    
    @Test
    fun `allowOperation - maneja identificador vacío correctamente`() {
        // Given: Identificador vacío (aunque no debería usarse en producción)
        val emptyIdentifier = ""
        
        // When & Then: Debe funcionar igual que cualquier otro
        repeat(MAX_REQUESTS) {
            assertTrue(
                "Identificador vacío debe funcionar",
                rateLimiter.allowOperation(emptyIdentifier)
            )
        }
        
        // Should block after limit
        assertFalse(
            "Debe bloquear después del límite",
            rateLimiter.allowOperation(emptyIdentifier)
        )
    }

    @Test
    fun `getCurrentCount - retorna 0 para identificador no usado`() {
        // Given: Identificador nunca usado
        val unusedIdentifier = "never-used"
        
        // When: Obtener contador
        val count = rateLimiter.getCurrentCount(unusedIdentifier)
        
        // Then: Debe ser 0
        assertEquals("Contador de ID no usado debe ser 0", 0, count)
    }

    @Test
    fun `getCurrentCount - actualiza después de permitir operación`() {
        // Given: Identificador limpio
        assertEquals(0, rateLimiter.getCurrentCount(TEST_IDENTIFIER))
        
        // When: Permitir 3 operaciones
        repeat(3) {
            rateLimiter.allowOperation(TEST_IDENTIFIER)
        }
        
        // Then: Contador debe reflejar operaciones
        assertEquals("Debe contar 3 operaciones", 3, rateLimiter.getCurrentCount(TEST_IDENTIFIER))
    }

    // ===== THREAD SAFETY =====
    // Nota: Tests de concurrencia serían útiles pero requieren setup más complejo
    // Para producción, considerar agregar:
    // - Test con múltiples threads
    // - Test de race conditions
    // - Property-based testing para edge cases
}
