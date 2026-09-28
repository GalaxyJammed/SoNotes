package com.example.sonotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sonotes.data.AppSettings
import com.example.sonotes.data.AppThemeMode
import com.example.sonotes.ui.BrowserScreen
import com.example.sonotes.ui.EditorScreen
import com.example.sonotes.ui.SettingsScreen
import com.example.sonotes.ui.theme.SoNotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(AppSettings.getThemeMode(context)) }
            val isDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            SoNotesTheme(darkTheme = isDarkTheme) {
                AppNav(onThemeChanged = { newMode -> themeMode = newMode })
            }
        }
    }
}

@Composable
fun AppNav(onThemeChanged: (AppThemeMode) -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "browser/-1") {
        composable(
            route = "browser/{folderId}",
            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
        ) { entry ->
            val folderId = entry.arguments?.getLong("folderId")?.takeIf { it != -1L }
            val back: (() -> Unit)? = if (folderId == null) null else ({ nav.popBackStack() })
            BrowserScreen(
                folderId = folderId,
                onOpenFolder = { nav.navigate("browser/$it") },
                onOpenNote = { nav.navigate("editor/$it/${folderId ?: -1L}") },
                onNewNote = { nav.navigate("editor/-1/${folderId ?: -1L}") },
                onOpenSettings = { nav.navigate("settings") },
                onBack = back
            )
        }
        composable(
            route = "editor/{noteId}/{folderId}",
            arguments = listOf(
                navArgument("noteId") { type = NavType.LongType },
                navArgument("folderId") { type = NavType.LongType }
            )
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: -1L
            val folderId = entry.arguments?.getLong("folderId")?.takeIf { it != -1L }
            EditorScreen(noteId = noteId, folderId = folderId, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onThemeChanged = onThemeChanged
            )
        }
    }
}
