package com.dynamictecnologies.notificationmanager.presentation.profile.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import com.dynamictecnologies.notificationmanager.presentation.profile.components.AddUserDialog

@Composable
fun ProfileScreen(
    userViewModel: UserViewModel,
    onLogout: () -> Unit
) {
    val userProfile by userViewModel.userProfile.collectAsState()
    val errorMessage by userViewModel.errorState.collectAsState()
    val isLoading by userViewModel.isLoading.collectAsState()
    var showAddUserDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Perfil de Usuario",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        userProfile?.let { profile ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Nombre de usuario: ${profile.username}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Email: ${profile.email}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } ?: run {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No hay perfil configurado",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { showAddUserDialog = true }
                    ) {
                        Text("Crear Perfil")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Cerrar Sesión")
        }
    }
    
    // Mostrar diálogo de agregar usuario
    if (showAddUserDialog) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onConfirm = { username ->
                userViewModel.registerUsername(username)
                showAddUserDialog = false
            }
        )
    }
}
