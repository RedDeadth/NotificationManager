package com.dynamictecnologies.notificationmanager.domain.entities

/**
 * Entidad de dominio que representa un dispositivo ESP32 vinculado.
 * 
 * Representa la vinculación más simple posible:
 * - Token de 8 caracteres generado por ESP32
 * - Topic MQTT derivado del token
 * - Sin userId, sin Firebase sync
 * 
 * - Inmutabilidad: Data class read-only
 * - Clean Architecture: Entidad pura sin dependencias
 * - Validación: Init block con validaciones estrictas
 * - Security: MAC address validation, token validation
 */
data class DevicePairing(
    val bluetoothName: String,      // "ESP32_A3F9"
    val bluetoothAddress: String,   // "XX:XX:XX:XX:XX:XX"
    val token: String,               // "A3F9K2L7" (8 caracteres)
    val mqttTopic: String,          // "n/A3F9K2L7"
    val pairedAt: Long               // Timestamp de vinculación
) {
    init {
        // Validación de bluetoothName
        require(bluetoothName.isNotBlank()) { 
            "Bluetooth name cannot be blank" 
        }
        require(bluetoothName.length <= MAX_BLUETOOTH_NAME_LENGTH) { 
            "Bluetooth name too long (max $MAX_BLUETOOTH_NAME_LENGTH chars)" 
        }
        
        // Validación de MAC address
        require(bluetoothAddress.matches(MAC_ADDRESS_REGEX)) { 
            "Invalid MAC address format. Expected: XX:XX:XX:XX:XX:XX" 
        }
        
        // Validación de token
        require(TokenValidator.validate(token)) { 
            "Invalid token. Must be 8 uppercase alphanumeric characters" 
        }
        
        // Validación de topic MQTT
        require(mqttTopic == TokenValidator.formatAsTopic(token)) { 
            "MQTT topic must match token format: n/$token" 
        }
        
        // Validación de timestamp
        require(pairedAt > 0) { 
            "Invalid pairing timestamp" 
        }
    }
    
    companion object {
        private val MAC_ADDRESS_REGEX = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
        private const val MAX_BLUETOOTH_NAME_LENGTH = 50
    }
}

/**
 * Validador de tokens de dispositivo.
 * 
 * Token format: 8 caracteres alfanuméricos uppercase
 * Ej: "A3F9K2L7"
 * 
 * - Stateless: Object sin estado
 */
object TokenValidator {
    const val TOKEN_LENGTH = 8
    private val TOKEN_REGEX = "^[A-Z0-9]{8}$".toRegex()
    
    /**
     * Valida formato de token
     */
    fun validate(token: String): Boolean {
        return token.length == TOKEN_LENGTH && TOKEN_REGEX.matches(token)
    }
    
    /**
     * Convierte token a topic MQTT
     * Ej: "A3F9K2L7" -> "n/A3F9K2L7"
     */
    fun formatAsTopic(token: String): String = "n/$token"
    
    /**
     * Extrae token de topic MQTT
     * Ej: "n/A3F9K2L7" -> "A3F9K2L7"
     */
    fun extractTokenFromTopic(topic: String): String? {
        return if (topic.startsWith("n/") && topic.length == TOKEN_LENGTH + 2) {
            topic.substring(2)
        } else {
            null
        }
    }
}

/**
 * Excepción para token inválido
 */
class InvalidTokenException(token: String) : Exception("Token inválido: $token. Debe ser 8 caracteres alfanuméricos.")

/**
 * Excepción para cuando no hay dispositivo vinculado
 */
class NoDevicePairedException : Exception("No hay dispositivo vinculado. Vincule un dispositivo primero.")
