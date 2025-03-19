package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import com.dynamictecnologies.notificationmanager.ui.components.InitialSelectionCard
import com.dynamictecnologies.notificationmanager.ui.components.AppSelectionDialog
import com.dynamictecnologies.notificationmanager.ui.components.AppBottomBar
import com.dynamictecnologies.notificationmanager.ui.components.AppTopBar
import com.dynamictecnologies.notificationmanager.ui.components.NotificationHistoryCard
import com.dynamictecnologies.notificationmanager.ui.components.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    userViewModel: UserViewModel,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToShared: () -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showAppList by viewModel.showAppList.collectAsState()
    val notifications by viewModel.notifications.collectAsState()

    var showShareDialog by remember { mutableStateOf(false) }


    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    Scaffold(
        topBar = {
            AppTopBar(
                currentScreen = currentScreen,
                canShare = selectedApp != null,
                onShareClick = { showShareDialog = true }
            )
        },
        bottomBar = {
            AppBottomBar(
                currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    when (screen) {
                        Screen.HOME -> currentScreen = Screen.HOME
                        Screen.SHARED -> onNavigateToShared()
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                selectedApp?.let { app ->
                    // Diseño horizontal para ambas tarjetas al mismo nivel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Tarjeta izquierda - App Seleccionada
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Aplicación Seleccionada",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                app.icon?.let { icon ->
                                    Image(
                                        bitmap = icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.toggleAppList() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cambiar Aplicación")
                                }
                            }
                        }

                        // Tarjeta derecha - Visualizador
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Visualizador de Notificaciones",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Icon(
                                    imageVector = Icons.Default.ScreenShare,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Sin conexión",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { /* Función futura */ },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Conectar")
                                }
                            }
                        }
                    }

                    // Historial de notificaciones
                    NotificationHistoryCard(
                        notifications = notifications,
                        modifier = Modifier.weight(1f)
                    )
                } ?: run {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        InitialSelectionCard(
                            onSelectAppClick = { viewModel.toggleAppList() }
                        )
                    }
                }
            }

            if (isLoading) {
                LoadingScreen()
            }

            if (showAppList) {
                AppSelectionDialog(
                    apps = apps,
                    onAppSelected = { app ->
                        viewModel.selectApp(app)
                        viewModel.toggleAppList()
                    },
                    onDismiss = { viewModel.toggleAppList() }
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}