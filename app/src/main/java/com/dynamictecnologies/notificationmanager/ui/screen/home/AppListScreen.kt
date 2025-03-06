package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.ui.components.ShareDialog
import com.dynamictecnologies.notificationmanager.ui.components.UsernameRegistrationDialog
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UsernameState
import com.dynamictecnologies.notificationmanager.ui.components.InitialSelectionCard
import com.dynamictecnologies.notificationmanager.ui.components.SelectedAppCard
import com.dynamictecnologies.notificationmanager.ui.components.AppSelectionDialog
import com.dynamictecnologies.notificationmanager.ui.components.UserDrawerContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    userViewModel: UserViewModel,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showAppList by viewModel.showAppList.collectAsState()
    val notifications by viewModel.notifications.collectAsState()

    var showShareDialog by remember { mutableStateOf(false) }
    val usernameState by userViewModel.usernameState.collectAsState()
    val sharedUsers by userViewModel.sharedUsers.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    var showProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Manager") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (selectedApp != null) {
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(Icons.Default.Home, contentDescription = "Inicio")
                    },
                    label = { Text("Inicio") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(Icons.Default.People, contentDescription = "Compartidos")
                    },
                    label = { Text("Compartidos") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        onNavigateToProfile()
                    },
                    icon = {
                        Icon(Icons.Default.Person, contentDescription = "Perfil")
                    },
                    label = { Text("Perfil") }
                )
            }
        }
    ) { paddingValues ->

            if (usernameState is UsernameState.Initial) {
                UsernameRegistrationDialog(
                    onUsernameSubmit = userViewModel::registerUsername,
                    state = usernameState
                )
            }

            if (showShareDialog) {
                ShareDialog(
                    onDismiss = { showShareDialog = false },
                    onShareWith = { username ->
                        userViewModel.shareWithUser(username)
                        showShareDialog = false
                    },
                    viewModel = userViewModel,
                    sharedUsers = sharedUsers
                )
            }

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