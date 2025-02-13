package com.dynamictecnologies.notificationmanager

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp {
                AppListScreen()
            }
        }
    }
}

@Composable
fun MyApp(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedApp by remember { mutableStateOf<String?>(null) }
    var showAppList by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            getInstalledApps(context.packageManager)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Manager") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAppList = !showAppList }
            ) {
                Text("Seleccionar app")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            Column(modifier = Modifier.align(Alignment.Center)) {
                if (selectedApp == null) {
                    Text(
                        text = "Seleccionar una aplicación",
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Text(
                        text = "Aplicación seleccionada: $selectedApp",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = showAppList,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 500.dp)
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 8.dp
                ) {
                    LazyColumn {
                        items(apps) { app ->
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        selectedApp = app.name
                                        showAppList = false
                                    }
                                    .padding(16.dp)
                            ) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = app.name)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class AppInfo(val name: String, val icon: androidx.compose.ui.graphics.ImageBitmap)

fun getInstalledApps(packageManager: PackageManager): List<AppInfo> {
    val apps = mutableListOf<AppInfo>()
    val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    for (packageInfo in packages) {
        if (packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null &&
            (packageInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
            val appName = packageInfo.loadLabel(packageManager).toString()
            val appIcon = packageInfo.loadIcon(packageManager).toBitmap().asImageBitmap()
            apps.add(AppInfo(appName, appIcon))
        }
    }
    return apps
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyApp {
        AppListScreen()
    }
}