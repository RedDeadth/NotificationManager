package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.ui.components.ShareDialog
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import com.dynamictecnologies.notificationmanager.ui.components.AppBottomBar
import com.dynamictecnologies.notificationmanager.ui.components.AppTopBar
import com.dynamictecnologies.notificationmanager.ui.components.Screen
import com.dynamictecnologies.notificationmanager.ui.screen.home.DateUtils.formatDate
import com.dynamictecnologies.notificationmanager.viewmodel.SharedScreenState
import com.dynamictecnologies.notificationmanager.viewmodel.ShareViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    userViewModel: UserViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val sharedUsers by viewModel.sharedUsers.collectAsState()
    val sharedWithMeNotifications by viewModel.sharedWithMeNotifications.collectAsState()
    
    // Add state for showing the share dialog
    var showShareDialog by remember { mutableStateOf(false) }

    val currentScreen = Screen.SHARED

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
                        Screen.SHARED -> { /* Ya estamos en esta pantalla */ }
                        Screen.PROFILE -> onNavigateToProfile()
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
            when (uiState) {
                is SharedScreenState.Loading -> {
                    LoadingIndicator()
                }
                is SharedScreenState.NoProfile -> {
                    NoProfileView(onNavigateToProfile)
                }
                is SharedScreenState.Success -> {
                    SharedScreenContent(
                        sharedUsers = sharedUsers,
                        sharedNotifications = sharedWithMeNotifications,
                        onAddUser = { showShareDialog = true } // Update to show dialog
                    )
                    
                    // Add ShareDialog when showShareDialog is true
                    if (showShareDialog) {
                        ShareDialog(
                            onDismiss = { showShareDialog = false },
                            onShareWith = { username ->
                                viewModel.shareWithUser(username)
                                showShareDialog = false
                            },
                            viewModel = userViewModel,
                            sharedUsers = sharedUsers
                        )
                    }
                }
                is SharedScreenState.Error -> {
                    ErrorView(message = (uiState as SharedScreenState.Error).message)
                }
            }
        }
    }
}

@Composable
private fun SharedScreenContent(
    sharedUsers: List<UserInfo>,
    sharedNotifications: Map<String, List<NotificationInfo>>,
    onAddUser: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sección de usuarios invitados
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Usuarios Invitados",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = onAddUser) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (sharedUsers.isEmpty()) {
                    Text(
                        text = "No has compartido con ningún usuario",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(sharedUsers) { user ->
                            SharedUserItem(user)
                            Divider()
                        }
                    }
                }
            }
        }

        // Sección de notificaciones compartidas conmigo
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Información compartida conmigo",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (sharedNotifications.isEmpty()) {
                    Text(
                        text = "No hay notificaciones compartidas contigo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        sharedNotifications.forEach { (username, notifications) ->
                            item {
                                Text(
                                    text = "De: $username",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(notifications) { notification ->
                                SharedNotificationItem(notification)
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedUserItem(user: UserInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
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
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = user.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SharedNotificationItem(notification: NotificationInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = notification.appName,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = formatDate(notification.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = notification.title,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = notification.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoProfileView(onNavigateToProfile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Primero debes crear un perfil",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onNavigateToProfile) {
            Text("Crear Perfil")
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

object DateUtils {
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun formatDate(date: Date): String {
        return dateFormatter.format(date)
    }

    fun formatTimestamp(timestamp: Long): String {
        return formatDate(Date(timestamp))
    }
}