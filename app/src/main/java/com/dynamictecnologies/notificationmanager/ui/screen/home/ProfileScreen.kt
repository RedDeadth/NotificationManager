package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction  // Corrección aquí
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    
    // Estado para evitar múltiples cargas
    var hasAttemptedLoad by remember { mutableStateOf(false) }

    // Cargar perfil solo la primera vez que se inicia la pantalla
    LaunchedEffect(Unit) {
        println("ProfileScreen: Iniciando carga de perfil")
        if (!hasAttemptedLoad) {
            onRefresh()
            hasAttemptedLoad = true
        }
    }
    
    // Debug del userInfo
    LaunchedEffect(userInfo) {
        if (userInfo != null) {
            println("ProfileScreen: Perfil cargado - username=${userInfo.username}, email=${userInfo.email}")
        } else {
            println("ProfileScreen: userInfo es null")
        }
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
            
            // Botón de recargar solo si ya hay un perfil, fuera del AppTopBar
            if (userInfo != null && !isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar perfil")
                    }
                }
            }
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
            // Primero determinamos si hay un perfil o no
            if (userInfo != null) {
                // Mostrar perfil si está disponible
                ProfileContent(
                    userInfo = userInfo,
                    onLogout = onLogout,
                    isLoading = isLoading
                )
                
                // Mostrar indicador de carga como overlay si está actualizando
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // Si no hay perfil y está cargando, mostrar loading spinner
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                    // Mostrar formulario de creación si no hay perfil y no está cargando
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
    onLogout: () -> Unit,
    isLoading: Boolean = false
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
    onCreateProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Crear Perfil",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { value ->
                // Eliminar espacios y caracteres no permitidos inmediatamente
                val filtered = value.replace("\\s+".toRegex(), "")
                    .filter { it.isLetterOrDigit() }
                onUsernameChange(filtered)

                // Mostrar error si intentan poner espacios
                if (value.contains(" ")) {
                    showError = "No se permiten espacios"
                } else {
                    showError = null
                }
            },
            label = { Text("Nombre de usuario") },
            supportingText = {
                Column {
                    Text("Solo letras y números permitidos")
                    showError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            isError = isError || showError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,  // Corregido aquí
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Text
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (username.length >= 3 && !username.contains(" ")) {
                        onCreateProfile()
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        Button(
            onClick = onCreateProfile,
            enabled = username.length >= 3 && !username.contains(" "),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Crear Perfil")
        }
    }
}