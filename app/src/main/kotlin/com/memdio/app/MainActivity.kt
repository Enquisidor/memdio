package com.memdio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.memdio.app.ui.settings.SettingsScreen
import com.memdio.app.ui.settings.SettingsViewModel
import com.memdio.app.ui.theme.MemdioTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemdioTheme {
                val navController = rememberNavController()
                // V1: single destination. V1.1 will add "clipEditor".
                NavHost(navController = navController, startDestination = "settings") {
                    composable("settings") {
                        val viewModel: SettingsViewModel = hiltViewModel()
                        SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
