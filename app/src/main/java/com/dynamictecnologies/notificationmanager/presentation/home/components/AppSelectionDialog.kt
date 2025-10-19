package com.dynamictecnologies.notificationmanager.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.data.model.AppInfo

@Composable
fun AppSelectionDialog(
    apps: List<AppInfo>,
    onAppSelected: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Seleccionar Aplicación")
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(apps) { app ->
                    AppListItem(
                        app = app,
                        onClick = {
                            onAppSelected(app)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
