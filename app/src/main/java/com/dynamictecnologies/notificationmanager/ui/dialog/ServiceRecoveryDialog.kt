package com.dynamictecnologies.notificationmanager.ui.dialog

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Diálogo Compose bloqueante que se muestra cuando el servicio se detiene.
 * 
 * Características:
 * - No dismissible (no se puede cerrar)
 * - Dos opciones obligatorias: Reiniciar o Entendido
 * - Persiste la elección del usuario
 */
object ServiceRecoveryDialog {
    
    enum class Action {
        RESTART,
        ACKNOWLEDGE
    }
    
    enum class StopReason {
        USER_STOPPED,
        SYSTEM_KILLED,
        EXTERNAL_SERVICE,
        PERMISSION_REVOKED,
        CRASH,
        UNKNOWN
    }
    
    private const val PREFS_NAME = "service_recovery_prefs"
    private const val KEY_AUTO_RESTART = "auto_restart_enabled"
    
    fun isAutoRestartEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RESTART, false)
    }
    
    fun setAutoRestartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_RESTART, enabled)
            .apply()
    }
}

@Composable
fun ServiceRecoveryDialogContent(
    reason: ServiceRecoveryDialog.StopReason = ServiceRecoveryDialog.StopReason.UNKNOWN,
    onActionSelected: (ServiceRecoveryDialog.Action) -> Unit
) {
    var showDialog by remember { mutableStateOf(true) }
    
    if (showDialog) {
        Dialog(
            onDismissRequest = { /* No dismissible */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    val (title, message) = getDialogContent(reason)
                    
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Message
                    Text(
                        text = message,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Start,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Restart Button
                    Button(
                        onClick = {
                            showDialog = false
                            onActionSelected(ServiceRecoveryDialog.Action.RESTART)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text(
                            text = "Reiniciar Servicio",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Acknowledge Button
                    TextButton(
                        onClick = {
                            showDialog = false
                            onActionSelected(ServiceRecoveryDialog.Action.ACKNOWLEDGE)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Entendido",
                            fontSize = 14.sp
                        )
                    }
                    
                    Text(
                        text = "Debes seleccionar una opcion para continuar",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

private fun getDialogContent(reason: ServiceRecoveryDialog.StopReason): Pair<String, String> {
    return when (reason) {
        ServiceRecoveryDialog.StopReason.USER_STOPPED -> Pair(
            "Servicio Detenido",
            "Has detenido el servicio de monitoreo de notificaciones.\n\nLas notificaciones no se capturarán hasta que lo reinicies."
        )
        
        ServiceRecoveryDialog.StopReason.SYSTEM_KILLED -> Pair(
            "Servicio Interrumpido",
            "El sistema Android detuvo el servicio para ahorrar bateria o memoria.\n\nRecomendacion: Agrega la app a la lista blanca de optimizacion."
        )
        
        ServiceRecoveryDialog.StopReason.EXTERNAL_SERVICE -> Pair(
            "Servicio Detenido Externamente",
            "Una aplicacion externa (antivirus, task killer) detuvo el servicio.\n\nConfigura excepciones en apps de limpieza."
        )
        
        ServiceRecoveryDialog.StopReason.PERMISSION_REVOKED -> Pair(
            "Permiso Revocado",
            "El permiso de acceso a notificaciones fue revocado.\n\nNecesitas otorgarlo nuevamente."
        )
        
        ServiceRecoveryDialog.StopReason.CRASH -> Pair(
            "Error en el Servicio",
            "El servicio encontro un error y se detuvo.\n\nSe enviara un reporte automatico."
        )
        
        ServiceRecoveryDialog.StopReason.UNKNOWN -> Pair(
            "Servicio No Activo",
            "El servicio de monitoreo dejó de funcionar.\n\nEsto puede afectar la captura de notificaciones."
        )
    }
}
