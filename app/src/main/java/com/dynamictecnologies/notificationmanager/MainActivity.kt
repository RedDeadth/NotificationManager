package com.dynamictecnologies.notificationmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.dynamictecnologies.notificationmanager.ui.theme.AppListScreen
import com.dynamictecnologies.notificationmanager.ui.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: AppListViewModel by viewModels {
        AppListViewModelFactory(packageManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp {
                AppListScreen(viewModel)
            }
        }
    }
}

@Composable
fun MyApp(content: @Composable () -> Unit) {
    NotificationManagerTheme {
        content()
    }
}