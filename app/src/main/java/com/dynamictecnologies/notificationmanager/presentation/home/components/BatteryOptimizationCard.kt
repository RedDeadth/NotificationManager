package com.dynamictecnologies.notificationmanager.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.util.PermissionHelper

/**
 * Tarjeta que muestra el estado de optimización de batería.
 * 
 * Si la app NO está exenta de optimización de batería:
 * - El servicio puede detenerse cuando el celular está bloqueado
 * - Muestra un botón para solicitar exención
 * 
 * Si la app ESTÁ exenta:
 * - Muestra estado verde indicando que está configurado correctamente
 */
@Composable
fun BatteryOptimizationCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isOptimized by remember { 
        mutableStateOf(PermissionHelper.hasBatteryOptimizationExemption(context)) 
    }
    
    // Actualizar estado cuando la pantalla se vuelve visible
    LaunchedEffect(Unit) {
        isOptimized = PermissionHelper.hasBatteryOptimizationExemption(context)
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOptimized) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icono de estado
            Icon(
                imageVector = if (isOptimized) 
                    Icons.Default.CheckCircle 
                else 
                    Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = if (isOptimized) 
                    Color(0xFF4CAF50)  // Verde
                else 
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            
            // Texto y botón
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isOptimized) 
                        "Optimización de batería desactivada" 
                    else 
                        "⚠️ Optimización de batería activa",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isOptimized) 
                        MaterialTheme.colorScheme.onSurfaceVariant 
                    else 
                        MaterialTheme.colorScheme.error
                )
                
                if (!isOptimized) {
                    Text(
                        text = "El servicio puede detenerse al bloquear el celular",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Botón de acción
            if (!isOptimized) {
                Button(
                    onClick = { 
                        PermissionHelper.requestBatteryOptimizationExemption(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Permitir")
                }
            } else {
                Text(
                    text = "✓ OK",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}
