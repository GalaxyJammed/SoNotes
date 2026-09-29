package com.example.sonotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.Folder
import com.example.sonotes.data.FolderTagCrossRef
import com.example.sonotes.data.Tag
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagBrowserScreen(
    tagId: Long,
    onOpenFolder: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
    onTagDeleted: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val allTags by database.tagDao().observeAllTags()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val currentTag = allTags.find { it.id == tagId }

    val tagFolders by database.tagDao().observeFoldersForTag(tagId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val allFolders by database.folderDao().observeAllFolders()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    val tagColor = tagColorToComposeColor(
        currentTag?.color ?: 0,
        MaterialTheme.colorScheme.primary
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Label,
                            contentDescription = null,
                            tint = tagColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(currentTag?.name ?: "Tag View")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open Sidebar")
                    }
                },
                actions = {
                    if (currentTag != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Tag")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Tag")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewFolderDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Folder")
            }
        }
    ) { padding ->
        if (tagFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        tint = tagColor.copy(alpha = 0.5f),
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        "No folders in this tag yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Manage Tag Folders")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(tagFolders, key = { "tag_folder_${it.id}" }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        leadingContent = {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        database.tagDao().deleteCrossRef(folder.id, tagId)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove from Tag",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            onOpenFolder(folder.id)
                        }
                    )
                }
            }
        }
    }

    if (showEditDialog && currentTag != null) {
        TagDialog(
            tagToEdit = currentTag,
            initialFolderIds = tagFolders.map { it.id },
            allFolders = allFolders,
            onDismiss = { showEditDialog = false },
            onSave = { name, color, selectedFolderIds ->
                scope.launch {
                    database.tagDao().update(currentTag.copy(name = name, color = color))
                    database.tagDao().setFoldersForTag(tagId, selectedFolderIds)
                }
                showEditDialog = false
            },
            onDelete = {
                showEditDialog = false
                showDeleteConfirmDialog = true
            }
        )
    }

    if (showDeleteConfirmDialog && currentTag != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Tag?") },
            text = { Text("Are you sure you want to delete \"${currentTag.name}\"? Folders in this tag will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            database.tagDao().delete(currentTag)
                        }
                        showDeleteConfirmDialog = false
                        onTagDeleted()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNewFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New folder in ${currentTag?.name ?: "Tag"}") },
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
                            val newFolderId = database.folderDao().insert(Folder(name = name.trim(), parentId = null))
                            database.tagDao().insertCrossRef(FolderTagCrossRef(folderId = newFolderId, tagId = tagId))
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
