package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.ui.components.AddUserDialog
import com.dynamictecnologies.notificationmanager.ui.components.AppBottomBar
import com.dynamictecnologies.notificationmanager.ui.components.AppTopBar
import com.dynamictecnologies.notificationmanager.ui.components.Screen
import com.dynamictecnologies.notificationmanager.viewmodel.ShareViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Composable
fun ShareScreen(
    shareViewModel: ShareViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToHome: () -> Unit,
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
                            style = MaterialTheme.typography.headlineSmall,
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
                                notifications = sharedUsersNotifications[user.uid] ?: emptyList(),
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
                            style = MaterialTheme.typography.headlineSmall,
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
                                notifications = sharedUsersNotifications[user.uid] ?: emptyList()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // Mantener el FloatingActionButton
        if (hasValidProfile && !isInitialLoading) {
            FloatingActionButton(
                onClick = {
                    shareViewModel.loadAvailableUsers()
                    showAddUserDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.PersonAdd, "Añadir usuario")
            }
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

@Composable
fun SharedUserCard(
    user: UserInfo,
    notifications: List<NotificationInfo>,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.titleMedium
                        )
                        user.email?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remover oyente",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
@Composable
fun ConductorNotificationsCard(
    user: UserInfo,
    notifications: List<NotificationInfo>
) {
    var expanded by remember { mutableStateOf(false) }

    val validNotifications = notifications.filter {
        val timestampMillis = it.timestamp?.time ?: 0L
        timestampMillis > 631152000000L // 01/01/1990 en milisegundos
    }
    
    // Tarjeta normal (contraída)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .clickable { expanded = true }, // Al hacer clic, expandir la vista
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Encabezado con información del usuario
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.username,
                        style = MaterialTheme.typography.titleMedium
                    )
                    user.email?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            if (validNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay notificaciones recientes disponibles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Notificaciones (${validNotifications.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Mostrar solo algunas notificaciones en la vista contraída
                Column {
                    validNotifications.take(2).forEach { notification ->
                        NotificationItem(notification)
                        if (notification != validNotifications.take(2).lastOrNull()) {
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Vista expandida como diálogo flotante
    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Encabezado con información del usuario y botón cerrar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notificaciones de ${user.username}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { expanded = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar"
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(bottom = 8.dp))

                    if (validNotifications.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay notificaciones disponibles",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Lista de todas las notificaciones en vista expandida
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            items(
                                items = validNotifications,
                            ) { notification ->
                                NotificationItem(notification, expanded = true)
                                Divider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: NotificationInfo,
    expanded: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Título de la notificación
        Text(
            text = notification.title.takeIf { it.isNotBlank() } ?: "Sin título",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Contenido de la notificación
        Text(
            text = notification.content.takeIf { it.isNotBlank() } ?: "Sin contenido",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Información adicional en la parte inferior
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Fecha formateada solo si está expandido
            if (expanded) {
                val timestamp = notification.timestamp
                val validTimestamp = timestamp != null && timestamp.time > 631152000000L
                
                if (validTimestamp) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}