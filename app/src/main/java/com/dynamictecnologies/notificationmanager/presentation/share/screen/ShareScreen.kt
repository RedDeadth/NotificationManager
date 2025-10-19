package com.dynamictecnologies.notificationmanager.presentation.share.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.presentation.share.components.*
import com.dynamictecnologies.notificationmanager.viewmodel.ShareViewModel
import kotlinx.coroutines.delay

@Composable
fun ShareScreen(
    shareViewModel: ShareViewModel,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedUsers by shareViewModel.sharedUsers.collectAsState()
    val sharedByUsers by shareViewModel.sharedByUsers.collectAsState()
    val availableUsers by shareViewModel.availableUsers.collectAsState()
    val isLoading by shareViewModel.isLoading.collectAsState()
    val error by shareViewModel.error.collectAsState()
    val successMessage by shareViewModel.successMessage.collectAsState()
    val sharedUsersNotifications by shareViewModel.sharedUsersNotifications.collectAsState()

    var showAddUserDialog by remember { mutableStateOf(false) }

    // Estado para controlar si el perfil del usuario está completo
    var hasValidProfile by remember { mutableStateOf(true) }

    // Estado adicional para controlar la carga inicial
    var isInitialLoading by remember { mutableStateOf(true) }

    // Verificar si el perfil del usuario está completo al cargar la pantalla
    LaunchedEffect(Unit) {
        try {
            // Marcar como carga inicial
            isInitialLoading = true

            // Verificar perfil silenciosamente
            hasValidProfile = shareViewModel.hasValidUserProfile()

            if (hasValidProfile) {
                // Solo cargar datos si el perfil es válido
                shareViewModel.setupSharedUsersObserver()
                shareViewModel.loadUsersWhoSharedWithMe()
            }
        } catch (e: Exception) {
            // Ignorar errores durante la carga inicial para evitar mostrarlos al usuario
            hasValidProfile = false
        } finally {
            // Finalizar la carga inicial después de un breve retraso para evitar parpadeos
            delay(300)
            isInitialLoading = false
        }
    }

    // Controlador para Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Efecto para mostrar mensajes de éxito como Snackbar
    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            // Limpiar mensaje después de mostrar
            shareViewModel.clearSuccessMessage()
        }
    }

    // Mostrar error solo si no estamos en carga inicial y hay un error real para mostrar
    if (!isInitialLoading && error != null) {
        AlertDialog(
            onDismissRequest = { shareViewModel.clearError() },
            title = { Text("Error") },
            text = { Text(error ?: "") },
            confirmButton = {
                TextButton(onClick = { shareViewModel.clearError() }) {
                    Text("Aceptar")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Si estamos en carga inicial, solo mostrar indicador de carga
        if (isInitialLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (!hasValidProfile) {
            // Mostrar mensaje de configuración de perfil si el perfil no es válido
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Completa tu perfil",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Para compartir con otros usuarios, primero debes completar tu perfil",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onNavigateToProfile
                ) {
                    Text("Ir al perfil")
                }
            }
        } else if (isLoading && !isInitialLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Contenido principal
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp)
                ) {
                    // Sección 1: "Mis notificaciones compartidas"
                    item {
                        Text(
                            text = "Lista de oyentes",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (sharedUsers.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "Aún no has añadido ningún oyente",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Pulsa el botón + para añadir usuarios a tu lista de oyentes",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(sharedUsers) { user ->
                            SharedUserCard(
                                user = user,
                                notifications = sharedUsersNotifications[user.id] ?: emptyList(),
                                onRemove = { shareViewModel.removeSharedUser(user.username) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Separador
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Sección 2: "Notificaciones compartidas conmigo"
                    item {
                        Text(
                            text = "Contenido de Conductores",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    if (sharedByUsers.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "Ningún conductor comparte contigo",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Cuando un conductor te añada a su lista de oyentes, aparecerá aquí",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(sharedByUsers) { user ->
                            ConductorNotificationsCard(
                                user = user,
                                notifications = sharedUsersNotifications[user.id] ?: emptyList()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // Mantener el FloatingActionButton
        if (hasValidProfile && !isInitialLoading) {
            ExtendedFloatingActionButton(
                onClick = {
                    shareViewModel.loadAvailableUsers()
                    showAddUserDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Añadir usuario"
                    )
                },
                text = { Text("Añadir oyente") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // SnackbarHost para mostrar mensajes
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showAddUserDialog) {
        AddUserDialog(
            availableUsers = availableUsers,
            isLoading = isLoading,
            onDismiss = { showAddUserDialog = false },
            onAddUser = { username ->
                shareViewModel.shareWithUser(username)
                showAddUserDialog = false
            }
        )
    }
}