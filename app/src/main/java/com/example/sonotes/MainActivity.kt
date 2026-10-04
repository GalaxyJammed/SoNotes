package com.example.sonotes

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.AppFontOption
import com.example.sonotes.data.AppSettings
import com.example.sonotes.data.AppThemeMode
import com.example.sonotes.data.Folder
import com.example.sonotes.data.Tag
import com.example.sonotes.ui.BrowserScreen
import com.example.sonotes.ui.EditorScreen
import com.example.sonotes.ui.LockScreen
import com.example.sonotes.ui.SearchScreen
import com.example.sonotes.ui.SettingsScreen
import com.example.sonotes.ui.TagBrowserScreen
import com.example.sonotes.ui.TagDialog
import com.example.sonotes.ui.TrashScreen
import com.example.sonotes.ui.tagColorToComposeColor
import com.example.sonotes.ui.theme.SoNotesTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(AppSettings.getThemeMode(context)) }
            var fontOption by remember { mutableStateOf(AppSettings.getAppFont(context)) }

            val lifecycleOwner = LocalLifecycleOwner.current
            val initialFingerprint = AppSettings.isFingerprintEnabled(context)
            val initialPin = AppSettings.isPinEnabled(context)
            val initialPinCode = AppSettings.getPinCode(context)

            var isLocked by remember {
                mutableStateOf(initialFingerprint || (initialPin && initialPinCode.isNotBlank()))
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        val fingerprintNow = AppSettings.isFingerprintEnabled(context)
                        val pinNow = AppSettings.isPinEnabled(context)
                        val pinCodeNow = AppSettings.getPinCode(context)
                        if (fingerprintNow || (pinNow && pinCodeNow.isNotBlank())) {
                            isLocked = true
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            SoNotesTheme(themeMode = themeMode, fontOption = fontOption) {
                val fingerprintNow = AppSettings.isFingerprintEnabled(context)
                val pinNow = AppSettings.isPinEnabled(context)
                val pinCodeNow = AppSettings.getPinCode(context)

                if (isLocked && (fingerprintNow || (pinNow && pinCodeNow.isNotBlank()))) {
                    LockScreen(
                        isFingerprintEnabled = fingerprintNow,
                        isPinEnabled = pinNow,
                        correctPin = pinCodeNow,
                        onUnlockSuccess = { isLocked = false }
                    )
                } else {
                    AppNav(
                        onThemeChanged = { newMode -> themeMode = newMode },
                        onFontChanged = { newFont -> fontOption = newFont }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(
    onThemeChanged: (AppThemeMode) -> Unit,
    onFontChanged: (AppFontOption) -> Unit
) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }

    val allFolders by database.folderDao().observeChildren(null)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val allTags by database.tagDao().observeAllTags()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val allCrossRefs by database.tagDao().observeAllCrossRefs()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val trashedFolders by database.folderDao().observeTrashedFolders()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val trashedNotes by database.noteDao().observeTrashedNotes()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val totalTrashedCount = trashedFolders.size + trashedNotes.size

    LaunchedEffect(Unit) {
        database.purgeOldTrash()
    }

    val navBackStackEntry by nav.currentBackStackEntryFlow.collectAsStateWithLifecycle(initialValue = nav.currentBackStackEntry)
    val currentRoute = navBackStackEntry?.destination?.route ?: ""
    val isParent = navBackStackEntry?.arguments?.let {
        if (it.containsKey("isParent")) it.getBoolean("isParent") else true
    } ?: true
    val gesturesEnabled = (currentRoute.startsWith("browser") || currentRoute.startsWith("tag") || currentRoute == "trash") && isParent

    var expandedTagIds by remember { mutableStateOf(setOf<Long>()) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewTagDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            scope.launch { drawerState.close() }
                            nav.navigate("browser/-1?isParent=true") {
                                popUpTo("browser/-1?isParent=true") { inclusive = true }
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    val primaryThemeColor = MaterialTheme.colorScheme.primary

                    LazyColumn(Modifier.weight(1f)) {
                        item {
                            Text("Tags", style = MaterialTheme.typography.titleMedium, color = primaryThemeColor)
                            Spacer(Modifier.height(4.dp))
                        }

                        if (allTags.isEmpty()) {
                            item {
                                Text(
                                    "No tags yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                                )
                            }
                        } else {
                            allTags.forEach { tag ->
                                val tagColor = tagColorToComposeColor(tag.color, primaryThemeColor)
                                val isExpanded = expandedTagIds.contains(tag.id)
                                val tagFolderIds = allCrossRefs.filter { it.tagId == tag.id }.map { it.folderId }
                                val tagFolders = allFolders.filter { tagFolderIds.contains(it.id) }

                                item(key = "drawer_t_${tag.id}") {
                                    ListItem(
                                        headlineContent = { Text(tag.name) },
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Label,
                                                contentDescription = null,
                                                tint = tagColor
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    expandedTagIds = if (isExpanded) expandedTagIds - tag.id else expandedTagIds + tag.id
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = if (isExpanded) "Collapse Tag" else "Expand Tag"
                                                )
                                            }
                                        },
                                        modifier = Modifier.clickable {
                                            scope.launch { drawerState.close() }
                                            nav.navigate("tag/${tag.id}?isParent=true")
                                        }
                                    )
                                }

                                if (isExpanded) {
                                    if (tagFolders.isEmpty()) {
                                        item(key = "drawer_t_${tag.id}_empty") {
                                            Text(
                                                "No folders added",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 56.dp, top = 2.dp, bottom = 6.dp)
                                            )
                                        }
                                    } else {
                                        items(tagFolders, key = { "drawer_t_${tag.id}_f_${it.id}" }) { f ->
                                            ListItem(
                                                headlineContent = { Text(f.name) },
                                                leadingContent = {
                                                    Icon(
                                                        Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                },
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                                modifier = Modifier
                                                    .padding(start = 24.dp)
                                                    .clickable {
                                                        scope.launch { drawerState.close() }
                                                        nav.navigate("browser/${f.id}?isParent=true")
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("Notebooks", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                        }

                        if (allFolders.isEmpty()) {
                            item {
                                Text(
                                    "No notebooks yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                                )
                            }
                        } else {
                            items(allFolders, key = { "drawer_f_${it.id}" }) { f ->
                                ListItem(
                                    headlineContent = { Text(f.name) },
                                    leadingContent = { Icon(Icons.Default.Folder, null) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable {
                                        scope.launch { drawerState.close() }
                                        nav.navigate("browser/${f.id}?isParent=true")
                                    }
                                )
                            }
                        }

                        item {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("Trash Bin", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                        }

                        item(key = "drawer_trash_bin") {
                            ListItem(
                                headlineContent = { Text("Trash Bin") },
                                supportingContent = {
                                    Text(
                                        if (totalTrashedCount == 0) "Empty"
                                        else "$totalTrashedCount item${if (totalTrashedCount == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Trash Bin",
                                        tint = if (totalTrashedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    scope.launch { drawerState.close() }
                                    nav.navigate("trash")
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

                    TextButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            showNewTagDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("New Tag")
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
                    onOpenSearch = { nav.navigate("search") },
                    onBack = back
                )
            }
            composable(
                route = "tag/{tagId}?isParent={isParent}",
                arguments = listOf(
                    navArgument("tagId") { type = NavType.LongType },
                    navArgument("isParent") { type = NavType.BoolType; defaultValue = true }
                )
            ) { entry ->
                val tagId = entry.arguments?.getLong("tagId") ?: -1L
                TagBrowserScreen(
                    tagId = tagId,
                    onOpenFolder = { folderId -> nav.navigate("browser/$folderId?isParent=true") },
                    onOpenDrawer = {
                        if (!drawerState.isAnimationRunning) {
                            scope.launch { drawerState.open() }
                        }
                    },
                    onOpenSearch = { nav.navigate("search") },
                    onTagDeleted = {
                        nav.popBackStack()
                    }
                )
            }
            composable("search") {
                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onOpenFolder = { folderId -> nav.navigate("browser/$folderId?isParent=false") },
                    onOpenNote = { noteId, folderId -> nav.navigate("editor/$noteId/${folderId ?: -1L}") }
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
                    onThemeChanged = onThemeChanged,
                    onFontChanged = onFontChanged
                )
            }
            composable("trash") {
                TrashScreen(
                    onOpenDrawer = {
                        if (!drawerState.isAnimationRunning) {
                            scope.launch { drawerState.open() }
                        }
                    }
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

    if (showNewTagDialog) {
        TagDialog(
            allFolders = allFolders,
            onDismiss = { showNewTagDialog = false },
            onSave = { name, color, selectedFolderIds ->
                scope.launch {
                    val newTagId = database.tagDao().insert(Tag(name = name, color = color))
                    database.tagDao().setFoldersForTag(newTagId, selectedFolderIds)
                }
                showNewTagDialog = false
            }
        )
    }
}
