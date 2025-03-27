package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
    
    // Estado para mostrar animación de refresco
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Estado para detectar nuevas notificaciones
    val notificationCounts = remember { mutableStateMapOf<String, Int>() }
    
    // Efecto para detectar cambios en las notificaciones
    LaunchedEffect(sharedUsersNotifications) {
        sharedUsersNotifications.forEach { (uid, notifications) ->
            val previousCount = notificationCounts[uid] ?: 0
            val currentCount = notifications.size
            
            if (previousCount > 0 && currentCount > previousCount) {
                // Mostrar animación de refresco si hay nuevas notificaciones
                isRefreshing = true
                delay(500) // Mantener la animación visible brevemente
                isRefreshing = false
            }
            
            // Actualizar el conteo
            notificationCounts[uid] = currentCount
        }
    }
    
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
            kotlinx.coroutines.delay(300)
            isInitialLoading = false
        }
    }
    
    // Configurar refresco periódico de datos
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000) // Actualizar cada 30 segundos
            if (hasValidProfile && !isInitialLoading) {
                // Mostrar animación sutil de actualización
                isRefreshing = true
                delay(500)
                isRefreshing = false
                
                // Refrescar datos
                shareViewModel.loadUsersWhoSharedWithMe()
            }
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
                // Botón de mantenimiento para migrar el formato de notificaciones
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indicador de actualización
                    AnimatedVisibility(
                        visible = isRefreshing,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Actualizando...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    TextButton(
                        onClick = { 
                            isRefreshing = true
                            shareViewModel.migrateNotificationsFormat()
                            kotlinx.coroutines.delay(500)
                            isRefreshing = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Reparar notificaciones")
                    }
                }
                
                // Botón para actualizar manualmente
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { 
                            isRefreshing = true
                            shareViewModel.loadUsersWhoSharedWithMe()
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                delay(500)
                                isRefreshing = false
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Actualizar")
                    }
                }
                
                // Contenido principal
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
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
                                    .padding(bottom = 16.dp)
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
                                    .padding(bottom = 16.dp)
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
                            SharedByUserCard(
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
fun SharedByUserCard(
    user: UserInfo,
    notifications: List<NotificationInfo>
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    // Estado para detectar nuevas notificaciones
    var previousCount by remember { mutableStateOf(0) }
    var hasNewNotifications by remember { mutableStateOf(false) }
    
    // Detectar cambios en las notificaciones
    LaunchedEffect(notifications) {
        if (previousCount > 0 && notifications.size > previousCount) {
            hasNewNotifications = true
            delay(5000) // Mantener el indicador por 5 segundos
            hasNewNotifications = false
        }
        previousCount = notifications.size
    }
    
    // Filtrar notificaciones válidas (con timestamp posterior a 1990)
    val validNotifications = notifications.filter { 
        val timestampMillis = it.timestamp?.time ?: 0L
        timestampMillis > 631152000000L // 01/01/1990 en milisegundos
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize() // Añadir animación de cambio de tamaño
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
                    Box {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        
                        // Mostrar indicador de nuevas notificaciones
                        if (hasNewNotifications) {
                            Badge(
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text("nuevo")
                            }
                        }
                    }
                    
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
                
                // Botón para expandir/colapsar
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Ver menos" else "Ver más")
                }
            }

            if (validNotifications.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notificaciones compartidas",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Contador de notificaciones
                    Text(
                        text = "${validNotifications.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Column {
                    // Mostrar 3 notificaciones si no está expandido, todas si está expandido
                    val notificationsToShow = if (isExpanded) validNotifications else validNotifications.take(3)
                    
                    notificationsToShow.forEach { notification ->
                        NotificationItem(notification)
                        Divider()
                    }
                    
                    // Mostrar indicador si hay más notificaciones
                    if (!isExpanded && validNotifications.size > 3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Hay ${validNotifications.size - 3} notificaciones más",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No hay notificaciones recientes disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
            .padding(vertical = 4.dp)
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
private fun NotificationItem(notification: NotificationInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = notification.title.takeIf { it.isNotBlank() } ?: "Sin título",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = notification.content.takeIf { it.isNotBlank() } ?: "Sin contenido",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        // Verificar timestamp válido (posterior a 1990)
        val timestamp = notification.timestamp
        val timestampMillis = timestamp?.time ?: 0L
        val validTimestamp = timestampMillis > 631152000000L // 01/01/1990 en milisegundos
        
        Text(
            text = if (validTimestamp) {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(timestamp)
            } else {
                "Fecha no disponible"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}