package com.dynamictecnologies.notificationmanager.ui.util.permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dynamictecnologies.notificationmanager.viewmodel.UsernameState

@Composable
fun UsernameRegistrationDialog(
    onUsernameSubmit: (String) -> Unit,
    state: UsernameState
) {
    var username by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* No permitir cerrar */ },
        title = {
            Text("Registra tu nombre de usuario")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        isError = false
                    },
                    label = { Text("Nombre de usuario") },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                when (state) {
                    is UsernameState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    is UsernameState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isBlank()) {
                        isError = true
                        return@Button
                    }
                    onUsernameSubmit(username)
                },
                enabled = state !is UsernameState.Loading
            ) {
                Text("Registrar")
            }
        }
    )
}