package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
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
    var showAddUserDialog by remember { mutableStateOf(false) }
    
    // Estado para controlar si el perfil del usuario está completo
    var hasValidProfile by remember { mutableStateOf(true) }
    
    // Verificar si el perfil del usuario está completo al cargar la pantalla
    LaunchedEffect(Unit) {
        hasValidProfile = shareViewModel.hasValidUserProfile()
        if (hasValidProfile) {
            // Solo cargar datos si el perfil es válido
            shareViewModel.setupSharedUsersObserver()
            shareViewModel.loadUsersWhoSharedWithMe() // Cargar usuarios que comparten con nosotros
        }
    }

    val currentScreen = Screen.SHARED
    
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

    // Mostrar error si existe
    error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { shareViewModel.clearError() },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { shareViewModel.clearError() }) {
                    Text("Aceptar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                currentScreen = currentScreen,
                canShare = false
            )
        },
        bottomBar = {
            AppBottomBar(
                currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    when (screen) {
                        Screen.HOME -> onNavigateToHome()
                        Screen.SHARED -> { /* Ya estamos aquí */ }
                        Screen.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        },
        floatingActionButton = {
            if (hasValidProfile) {
                FloatingActionButton(
                    onClick = {
                        shareViewModel.loadAvailableUsers()
                        showAddUserDialog = true
                    }
                ) {
                    Icon(Icons.Default.PersonAdd, "Añadir usuario")
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasValidProfile) {
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
            } else if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
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
                                notifications = shareViewModel.sharedUsersNotifications.value[user.uid] ?: emptyList(),
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
                                notifications = shareViewModel.sharedUsersNotifications.value[user.uid] ?: emptyList()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
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
}

@Composable
fun SharedByUserCard(
    user: UserInfo,
    notifications: List<NotificationInfo>
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
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
            }

            if (notifications.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Notificaciones compartidas",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column {
                    notifications.take(3).forEach { notification ->
                        NotificationItem(notification)
                        Divider()
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No hay notificaciones disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SharedUsersContent(
    sharedUsers: List<UserInfo>,
    sharedUsersNotifications: Map<String, List<NotificationInfo>>,
    onRemoveUser: (String) -> Unit
) {
    if (sharedUsers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No has añadido ningún oyente a tu lista",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sharedUsers) { user ->
                SharedUserCard(
                    user = user,
                    notifications = sharedUsersNotifications[user.uid] ?: emptyList(),
                    onRemove = { onRemoveUser(user.username) }
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
            text = notification.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = notification.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(notification.timestamp), // Aquí está el cambio
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}