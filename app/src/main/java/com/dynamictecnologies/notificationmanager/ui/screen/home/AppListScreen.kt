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
import androidx.compose.material.icons.filled.Menu
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
    onLogout: () -> Unit
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


    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                UserDrawerContent(
                    usernameState = usernameState,
                    onCreateProfile = { scope.launch { drawerState.close() } },
                    onLogout = {
                        scope.launch {
                            drawerState.close()
                            onLogout()
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Notification Manager") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
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
                    viewModel = userViewModel,  // Añadir este parámetro
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
                    // Cambiamos la forma de manejar selectedApp
                    selectedApp?.let { app ->
                        SelectedAppCard(
                            app = app,
                            onChangeAppClick = { viewModel.toggleAppList() }
                        )
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