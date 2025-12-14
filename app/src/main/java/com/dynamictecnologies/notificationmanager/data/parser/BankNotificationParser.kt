package com.dynamictecnologies.notificationmanager.data.parser

import java.math.BigDecimal
import java.util.regex.Pattern

/**
 * Parser para extraer información estructurada de notificaciones bancarias y de billeteras digitales.
 * 
 * Extrae:
 * - Monto de dinero ($XX.XX)
 * - Nombre de persona
 * - Código de transacción
 * - Tipo de operación (pago, transferencia, etc.)
 * 
 * Ejemplos soportados:
 * - "Annette P. Rodriguez: Te envió un pago por $11.18"
 * - "Transferencia recibida de $500.00 - Código: ABC123"
 * - "Pago exitoso por $25.50 a Juan Pérez"
 * 
 */
class BankNotificationParser {
    
    companion object {
        // Regex para montos: $XX.XX, $X,XXX.XX, USD XX.XX
        private val AMOUNT_PATTERNS = listOf(
            Pattern.compile("\\$\\s*([0-9,]+\\.?[0-9]{0,2})"),
            Pattern.compile("USD\\s*([0-9,]+\\.?[0-9]{0,2})"),
            Pattern.compile("([0-9,]+\\.?[0-9]{0,2})\\s*(?:dólares|pesos|soles)")
        )
        
        // Regex para códigos de transacción
        private val CODE_PATTERNS = listOf(
            Pattern.compile("(?:código|code|ref|referencia)\\s*:?\\s*([A-Z0-9]{4,20})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("#([A-Z0-9]{6,20})")
        )
        
        // Regex para nombres de personas (más flexible)
        private val NAME_PATTERNS = listOf(
            Pattern.compile("([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ]\\.?)?(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+)\\s*:"),
            Pattern.compile("(?:de|from|por)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ]\\.?)?(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+)"),
            Pattern.compile("(?:to|a)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ])?(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+)")
        )
        
        // Palabras clave para tipo de operación
        private val OPERATION_KEYWORDS = mapOf(
            "pago" to OperationType.PAYMENT,
            "payment" to OperationType.PAYMENT,
            "transferencia" to OperationType.TRANSFER,
            "transfer" to OperationType.TRANSFER,
            "envió" to OperationType.RECEIVED,
            "sent" to OperationType.SENT,
            "recibido" to OperationType.RECEIVED,
            "received" to OperationType.RECEIVED,
            "cobro" to OperationType.CHARGE,
            "charge" to OperationType.CHARGE
        )
    }
    
    enum class OperationType {
        PAYMENT,
        TRANSFER,
        RECEIVED,
        SENT,
        CHARGE,
        UNKNOWN
    }
    
    data class ParsedNotification(
        val amount: BigDecimal?,
        val currency: String = "USD",
        val personName: String?,
        val transactionCode: String?,
        val operationType: OperationType,
        val originalText: String,
        val confidence: Float // 0.0 - 1.0
    )
    
    /**
     * Parsea una notificación bancaria extrayendo información estructurada.
     */
    fun parse(title: String, content: String = ""): ParsedNotification {
        val fullText = "$title $content"
        
        val amount = extractAmount(fullText)
        val personName = extractPersonName(fullText)
        val transactionCode = extractTransactionCode(fullText)
        val operationType = detectOperationType(fullText)
        
        // Calcular confianza basada en cuántos campos se extrajeron
        val confidence = calculateConfidence(amount, personName, transactionCode, operationType)
        
        return ParsedNotification(
            amount = amount,
            personName = personName,
            transactionCode = transactionCode,
            operationType = operationType,
            originalText = fullText.trim(),
            confidence = confidence
        )
    }
    
    private fun extractAmount(text: String): BigDecimal? {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1)
                    ?.replace(",", "")
                    ?.replace(" ", "")
                
                return try {
                    BigDecimal(amountStr)
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }
    
    private fun extractPersonName(text: String): String? {
        for (pattern in NAME_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val name = matcher.group(1)?.trim()
                if (name != null && name.length >= 3) {
                    return name
                }
            }
        }
        return null
    }
    
    private fun extractTransactionCode(text: String): String? {
        for (pattern in CODE_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim()
            }
        }
        return null
    }
    
    private fun detectOperationType(text: String): OperationType {
        val lowerText = text.lowercase()
        
        for ((keyword, type) in OPERATION_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                return type
            }
        }
        
        return OperationType.UNKNOWN
    }
    
    private fun calculateConfidence(
        amount: BigDecimal?,
        personName: String?,
        transactionCode: String?,
        operationType: OperationType
    ): Float {
        var score = 0f
        
        if (amount != null) score += 0.4f // Monto es muy importante
        if (personName != null) score += 0.3f
        if (transactionCode != null) score += 0.2f
        if (operationType != OperationType.UNKNOWN) score += 0.1f
        
        return score
    }
    
    /**
     * Verifica si el texto parece ser una notificación bancaria.
     */
    fun isBankNotification(title: String, content: String = ""): Boolean {
        val fullText = "$title $content".lowercase()
        
        val bankKeywords = listOf(
            "pago", "payment", "transferencia", "transfer",
            "banco", "bank", "billetera", "wallet",
            "dinero", "money", "enviado", "recibido"
        )
        
        val hasKeyword = bankKeywords.any { fullText.contains(it) }
        val hasAmount = extractAmount(fullText) != null
        
        return hasKeyword || hasAmount
    }
}
