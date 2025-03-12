package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.ui.components.AppBottomBar
import com.dynamictecnologies.notificationmanager.ui.components.AppTopBar
import com.dynamictecnologies.notificationmanager.ui.components.Screen

@Composable
fun ProfileScreen(
    userInfo: UserInfo?,
    errorMessage: String?,
    isLoading: Boolean,
    onCreateProfile: (String) -> Unit,
    onRefresh: () -> Unit,
    onErrorDismiss: () -> Unit,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToShared: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    // Cargar perfil cuando se inicia la pantalla
    LaunchedEffect(Unit) {
        onRefresh()
    }

    // Mostrar loading
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Dialog de error
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = onErrorDismiss,
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = onErrorDismiss) {
                    Text("Aceptar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                currentScreen = Screen.PROFILE,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Regresar")
                    }
                }
            )
        },
        bottomBar = {
            AppBottomBar(
                currentScreen = Screen.PROFILE,
                onScreenSelected = { screen ->
                    when (screen) {
                        Screen.HOME -> onNavigateToHome()
                        Screen.SHARED -> onNavigateToShared()
                        Screen.PROFILE -> { /* Ya estamos aquí */ }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (userInfo != null) {
                ProfileContent(
                    userInfo = userInfo,
                    onLogout = onLogout
                )
            } else {
                CreateProfileForm(
                    username = username,
                    isError = isError,
                    onUsernameChange = {
                        username = it
                        isError = false
                    },
                    onCreateProfile = {
                        if (username.isBlank()) {
                            isError = true
                        } else {
                            onCreateProfile(username)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
@Composable
fun ProfileContent(
    userInfo: UserInfo,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Surface(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize(),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Información del usuario
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                ProfileInfoRow(
                    icon = Icons.Default.Person,
                    label = "Nombre de usuario",
                    value = userInfo.username
                )

                Spacer(modifier = Modifier.height(16.dp))

                ProfileInfoRow(
                    icon = Icons.Default.Email,
                    label = "Correo electrónico",
                    value = userInfo.email ?: "No disponible"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar Sesión")
        }
    }
}

@Composable
fun CreateProfileForm(
    username: String,
    isError: Boolean,
    onUsernameChange: (String) -> Unit,
    onCreateProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Crea tu perfil",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Nombre de usuario") },
            isError = isError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (isError) {
            Text(
                text = "El nombre de usuario no puede estar vacío",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCreateProfile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Crear Perfil")
        }
    }
}