package com.example.sonotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.AppThemeMode
import com.example.sonotes.data.AppSettings
import com.example.sonotes.data.Folder
import com.example.sonotes.ui.BrowserScreen
import com.example.sonotes.ui.EditorScreen
import com.example.sonotes.ui.SettingsScreen
import com.example.sonotes.ui.theme.SoNotesTheme
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(onThemeChanged: (AppThemeMode) -> Unit) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }

    val allFolders by database.folderDao().observeChildren(null)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val navBackStackEntry by nav.currentBackStackEntryFlow.collectAsStateWithLifecycle(initialValue = nav.currentBackStackEntry)
    val currentRoute = navBackStackEntry?.destination?.route ?: ""
    val isParent = navBackStackEntry?.arguments?.getBoolean("isParent") ?: true
    val gesturesEnabled = currentRoute.startsWith("browser") && isParent

    var showNewFolderDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SoNotes", style = MaterialTheme.colorScheme.primary.let { MaterialTheme.typography.titleLarge })
                        IconButton(onClick = {
                            scope.launch { drawerState.close() }
                            nav.navigate("settings")
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    ListItem(
                        headlineContent = { Text("All Notes") },
                        leadingContent = { Icon(Icons.Default.Description, null) },
                        modifier = Modifier.clickable {
                            scope.launch { drawerState.close() }
                            nav.navigate("browser/-1?isParent=true") {
                                popUpTo("browser/-1?isParent=true") { inclusive = true }
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Notebooks", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(allFolders, key = { "drawer_f${it.id}" }) { f ->
                            ListItem(
                                headlineContent = { Text(f.name) },
                                leadingContent = { Icon(Icons.Default.Folder, null) },
                                modifier = Modifier.clickable {
                                    scope.launch { drawerState.close() }
                                    nav.navigate("browser/${f.id}?isParent=true")
                                }
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            showNewFolderDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("New Notebook")
                    }
                }
            }
        }
    ) {
        NavHost(
            navController = nav,
            startDestination = "browser/-1?isParent=true",
            enterTransition = { slideInHorizontally(animationSpec = tween(250)) { it } },
            exitTransition = { slideOutHorizontally(animationSpec = tween(250)) { -it } },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(250)) { -it } },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(250)) { it } },
        ) {
            composable(
                route = "browser/{folderId}?isParent={isParent}",
                arguments = listOf(
                    navArgument("folderId") { type = NavType.LongType },
                    navArgument("isParent") { type = NavType.BoolType; defaultValue = true }
                )
            ) { entry ->
                val folderId = entry.arguments?.getLong("folderId")?.takeIf { it != -1L }
                val parentArg = entry.arguments?.getBoolean("isParent") ?: (folderId == null)
                val back: (() -> Unit)? = if (parentArg) null else ({ nav.popBackStack() })
                BrowserScreen(
                    folderId = folderId,
                    isParentFolder = parentArg,
                    onOpenFolder = { id, parent -> nav.navigate("browser/$id?isParent=$parent") },
                    onOpenNote = { nav.navigate("editor/$it/${folderId ?: -1L}") },
                    onNewNote = { nav.navigate("editor/-1/${folderId ?: -1L}") },
                    onOpenDrawer = {
                        if (!drawerState.isAnimationRunning) {
                            scope.launch { drawerState.open() }
                        }
                    },
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

    if (showNewFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New notebook") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            database.folderDao().insert(Folder(name = name.trim(), parentId = null))
                        }
                        showNewFolderDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }
}
