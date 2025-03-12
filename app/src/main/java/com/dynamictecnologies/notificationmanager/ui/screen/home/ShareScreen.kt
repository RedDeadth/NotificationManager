package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.ui.components.AddUserDialog
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import com.dynamictecnologies.notificationmanager.ui.components.AppBottomBar
import com.dynamictecnologies.notificationmanager.ui.components.AppTopBar
import com.dynamictecnologies.notificationmanager.ui.components.NotificationHistoryCard
import com.dynamictecnologies.notificationmanager.ui.components.Screen
import com.dynamictecnologies.notificationmanager.viewmodel.ShareViewModel
import com.dynamictecnologies.notificationmanager.ui.items.SharedUserItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShareScreen(
    shareViewModel: ShareViewModel,
    userViewModel: UserViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedUsers by shareViewModel.sharedUsers.collectAsState()
    val sharedUsersNotifications by shareViewModel.sharedUsersNotifications.collectAsState()
    val availableUsers by shareViewModel.availableUsers.collectAsState()
    val isLoadingUsers by shareViewModel.isLoading.collectAsState()
    var showAddFriendDialog by remember { mutableStateOf(false) }

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
            SharedScreenContent(
                sharedUsers = sharedUsers,
                availableUsers = availableUsers,
                sharedUsersNotifications = sharedUsersNotifications,
                isLoading = isLoadingUsers,
                onAddUserClick = {
                    shareViewModel.loadAvailableUsers()
                    showAddFriendDialog = true
                },
                onRemoveUser = { userId ->
                    shareViewModel.removeSharedUser(userId)
                }
            )

            if (showAddFriendDialog) {
                AddUserDialog(
                    availableUsers = availableUsers,
                    isLoading = isLoadingUsers,
                    onDismiss = { showAddFriendDialog = false },
                    onAddUser = { userId ->
                        shareViewModel.shareWithUser(userId)
                        showAddFriendDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SharedScreenContent(
    sharedUsers: List<UserInfo>,
    availableUsers: List<UserInfo>,
    isLoading: Boolean,
    sharedUsersNotifications: Map<String, List<NotificationInfo>>,
    onAddUserClick: () -> Unit,
    onRemoveUser: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
                        text = "Usuarios Compartidos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = onAddUserClick) {
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
                            SharedUserItem(
                                user = user,
                                onRemove = { onRemoveUser(user.username) }
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}
