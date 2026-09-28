package com.example.sonotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.Folder
import com.example.sonotes.data.Note
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    folderId: Long?,
    onOpenFolder: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onBack: (() -> Unit)?
) {
    val context = LocalContext.current
    val vm: BrowserViewModel = viewModel(key = "browser_$folderId") {
        BrowserViewModel(AppDatabase.get(context), folderId)
    }
    val folders by vm.folders.collectAsStateWithLifecycle()
    val allFolders by vm.allFolders.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SoNotes (Joplin Style)", style = MaterialTheme.colorScheme.primary.let { MaterialTheme.typography.titleLarge })
                        IconButton(onClick = {
                            scope.launch { drawerState.close() }
                            onOpenSettings?.invoke()
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
                            onOpenFolder(-1L) // or navigate to root
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
                                    onOpenFolder(f.id)
                                }
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            showFolderDialog = true
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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(vm.title) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Sidebar")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                Box {
                    FloatingActionButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("New folder") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            onClick = {
                                menuOpen = false
                                showFolderDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New note") },
                            leadingIcon = { Icon(Icons.Default.NoteAdd, null) },
                            onClick = {
                                menuOpen = false
                                onNewNote()
                            }
                        )
                    }
                }
            }
        ) { padding ->
            if (folders.isEmpty() && notes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Nothing here yet. Tap + to add a folder or note.")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(folders, key = { "f${it.id}" }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Icon(Icons.Default.Folder, null) },
                            trailingContent = {
                                IconButton(onClick = { folderToDelete = folder }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete folder")
                                }
                            },
                            modifier = Modifier.clickable { onOpenFolder(folder.id) }
                        )
                    }
                    items(notes, key = { "n${it.id}" }) { note ->
                        ListItem(
                            headlineContent = { Text(note.title.ifBlank { "Untitled" }) },
                            leadingContent = { Icon(Icons.Default.Description, null) },
                            trailingContent = {
                                IconButton(onClick = { noteToDelete = note }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete note")
                                }
                            },
                            modifier = Modifier.clickable { onOpenNote(note.id) }
                        )
                    }
                }
            }
        }
    }

    if (showFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("New folder") },
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
                        vm.addFolder(name)
                        showFolderDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete folder?") },
            text = { Text("\"${folder.name}\" and everything inside it will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolder(folder)
                    folderToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Cancel") }
            }
        )
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete note?") },
            text = { Text("\"${note.title.ifBlank { "Untitled" }}\" will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNote(note)
                    noteToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
