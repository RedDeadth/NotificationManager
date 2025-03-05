package com.dynamictecnologies.notificationmanager.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    onDismiss: () -> Unit,
    onShareWith: (String) -> Unit,
    viewModel: UserViewModel,
    sharedUsers: List<UserInfo>
) {
    val availableUsers by viewModel.availableUsers.collectAsState()
    var selectedUsername by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadAvailableUsers()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir Notificaciones") },
        text = {
            Column {
                if (availableUsers.isEmpty()) {
                    Text("No hay usuarios disponibles para compartir")
                } else {
                    OutlinedTextField(
                        value = selectedUsername,
                        onValueChange = { selectedUsername = it },
                        label = { Text("Usuario") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Usuarios disponibles:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(availableUsers) { username ->
                            Text(
                                text = username,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedUsername = username }
                                    .padding(vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedUsername.isNotEmpty()) {
                        onShareWith(selectedUsername)
                    }
                },
                enabled = selectedUsername.isNotEmpty()
            ) {
                Text("Compartir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}