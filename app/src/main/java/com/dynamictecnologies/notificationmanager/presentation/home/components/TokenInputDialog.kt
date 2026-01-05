package com.dynamictecnologies.notificationmanager.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.domain.entities.TokenValidator

/**
 * Dialog para ingreso de token de 6 caracteres.
 * 
 * Características:
 * - Validación en tiempo real
 * - Auto-uppercase
 * - Feedback visual de validez
 * 
 * - Stateless: No mantiene estado entre aperturas
 */
@Composable
fun TokenInputDialog(
    deviceName: String,
    onTokenEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var token by remember { mutableStateOf("") }
    val isValid = TokenValidator.validate(token)
    val remainingChars = TokenValidator.TOKEN_LENGTH - token.length
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Vincular Dispositivo")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Ingrese el token de 6 caracteres mostrado en la pantalla LCD del dispositivo:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                OutlinedTextField(
                    value = token,
                    onValueChange = { newToken ->
                        // Solo permitir alfanuméricos y máximo 8 caracteres
                        if (newToken.length <= TokenValidator.TOKEN_LENGTH && 
                            newToken.all { it.isLetterOrDigit() }) {
                            token = newToken.uppercase()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Ej: A3F9K2")
                    },
                    supportingText = {
                        Text(
                            text = when {
                                token.isEmpty() -> "Ingrese el token (${TokenValidator.TOKEN_LENGTH} caracteres)"
                                remainingChars > 0 -> "Faltan $remainingChars caracteres"
                                isValid -> "Token válido"
                                else -> "✗ Token inválido"
                            },
                            color = when {
                                isValid -> MaterialTheme.colorScheme.primary
                                token.isNotEmpty() && !isValid -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    isError = token.isNotEmpty() && !isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isValid) {
                                onTokenEntered(token)
                            }
                        }
                    )
                )
                
                // Indicador visual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(TokenValidator.TOKEN_LENGTH) { index ->
                        Box(
                            modifier = Modifier
                                .size(40.dp, 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        index < token.length -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (index < token.length) {
                                        Text(
                                            text = token[index].toString(),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onTokenEntered(token) },
                enabled = isValid
            ) {
                Text("Vincular")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
