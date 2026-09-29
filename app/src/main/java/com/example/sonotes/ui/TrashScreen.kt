package com.example.sonotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.Folder
import com.example.sonotes.data.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil

sealed class TrashListItem {
    abstract val id: String
    abstract val deletedAt: Long

    data class TrashedFolder(
        val folder: Folder,
        val noteCount: Int = 0,
        val subfolderCount: Int = 0
    ) : TrashListItem() {
        override val id: String get() = "tf_${folder.id}"
        override val deletedAt: Long get() = folder.deletedAt ?: 0L
    }

    data class TrashedNote(
        val note: Note
    ) : TrashListItem() {
        override val id: String get() = "tn_${note.id}"
        override val deletedAt: Long get() = note.deletedAt ?: 0L
    }
}

class TrashViewModel(
    private val db: AppDatabase
) : ViewModel() {

    private val allTrashedFolders = db.folderDao().observeTrashedFolders()
    private val allTrashedNotes = db.noteDao().observeTrashedNotes()

    val trashedItems: StateFlow<List<TrashListItem>> = combine(
        allTrashedFolders,
        allTrashedNotes
    ) { trashedFolders, trashedNotes ->
        val trashedFolderIds = trashedFolders.map { it.id }.toSet()

        val topLevelFolders = trashedFolders.filter { folder ->
            folder.parentId == null || !trashedFolderIds.contains(folder.parentId)
        }

        val topLevelNotes = trashedNotes.filter { note ->
            note.folderId == null || !trashedFolderIds.contains(note.folderId)
        }

        val folderItems = topLevelFolders.map { folder ->
            val childNotes = trashedNotes.count { isNoteDescendantOf(it, folder.id, trashedFolders) }
            val childFolders = trashedFolders.count { it.id != folder.id && isFolderDescendantOf(it, folder.id, trashedFolders) }
            TrashListItem.TrashedFolder(
                folder = folder,
                noteCount = childNotes,
                subfolderCount = childFolders
            )
        }

        val noteItems = topLevelNotes.map { note ->
            TrashListItem.TrashedNote(note = note)
        }

        val combined: List<TrashListItem> = folderItems + noteItems
        combined.sortedByDescending { it.deletedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun isNoteDescendantOf(note: Note, targetFolderId: Long, allFolders: List<Folder>): Boolean {
        var currentFolderId = note.folderId
        val visited = mutableSetOf<Long>()
        while (currentFolderId != null && visited.add(currentFolderId)) {
            if (currentFolderId == targetFolderId) return true
            val parent = allFolders.firstOrNull { it.id == currentFolderId }
            currentFolderId = parent?.parentId
        }
        return false
    }

    private fun isFolderDescendantOf(folder: Folder, targetFolderId: Long, allFolders: List<Folder>): Boolean {
        var currentFolderId = folder.parentId
        val visited = mutableSetOf<Long>()
        while (currentFolderId != null && visited.add(currentFolderId)) {
            if (currentFolderId == targetFolderId) return true
            val parent = allFolders.firstOrNull { it.id == currentFolderId }
            currentFolderId = parent?.parentId
        }
        return false
    }

    fun restoreFolder(folder: Folder) {
        viewModelScope.launch {
            restoreFolderRecursive(folder)
        }
    }

    private suspend fun restoreFolderRecursive(folder: Folder) {
        db.folderDao().restore(folder.id)

        var parentId = folder.parentId
        while (parentId != null) {
            val parent = db.folderDao().getById(parentId) ?: break
            if (parent.deletedAt != null) {
                db.folderDao().restore(parent.id)
                parentId = parent.parentId
            } else {
                break
            }
        }

        val trashedFolders = db.folderDao().getTrashedFoldersSync()
        val descendantFolders = trashedFolders.filter { isFolderDescendantOf(it, folder.id, trashedFolders) }
        descendantFolders.forEach { db.folderDao().restore(it.id) }

        val trashedNotes = db.noteDao().getTrashedNotesSync()
        val descendantNotes = trashedNotes.filter { isNoteDescendantOf(it, folder.id, trashedFolders + folder) }
        descendantNotes.forEach { db.noteDao().restore(it.id) }
    }

    fun restoreNote(note: Note) {
        viewModelScope.launch {
            db.noteDao().restore(note.id)
            note.folderId?.let { folderId ->
                val parentFolder = db.folderDao().getById(folderId)
                if (parentFolder?.deletedAt != null) {
                    restoreFolderRecursive(parentFolder)
                }
            }
        }
    }

    fun deleteFolderPermanently(folder: Folder) {
        viewModelScope.launch {
            deleteFolderPermanentlyRecursive(folder.id)
        }
    }

    private suspend fun deleteFolderPermanentlyRecursive(folderId: Long) {
        val allFolders = db.folderDao().getAllFoldersSync()
        val allNotes = db.noteDao().getAllNotesSync()

        val descendantFolders = allFolders.filter { it.id == folderId || isFolderDescendantOf(it, folderId, allFolders) }
        val descendantFolderIds = descendantFolders.map { it.id }.toSet()

        val descendantNotes = allNotes.filter { it.folderId != null && descendantFolderIds.contains(it.folderId) }

        descendantNotes.forEach { db.noteDao().deletePermanently(it.id) }
        descendantFolders.forEach { db.folderDao().deletePermanently(it.id) }
    }

    fun deleteNotePermanently(note: Note) {
        viewModelScope.launch {
            db.noteDao().deletePermanently(note.id)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val trashedNotes = db.noteDao().getTrashedNotesSync()
            val trashedFolders = db.folderDao().getTrashedFoldersSync()

            trashedNotes.forEach { db.noteDao().deletePermanently(it.id) }
            trashedFolders.forEach { db.folderDao().deletePermanently(it.id) }
        }
    }
}

fun calculateDaysRemaining(deletedAt: Long): Int {
    val retentionMs = 30L * 24 * 60 * 60 * 1000L
    val elapsed = System.currentTimeMillis() - deletedAt
    val remaining = retentionMs - elapsed
    if (remaining <= 0) return 0
    return ceil(remaining / (24.0 * 60 * 60 * 1000.0)).toInt().coerceIn(1, 30)
}

fun formatDaysRemainingText(deletedAt: Long): String {
    val days = calculateDaysRemaining(deletedAt)
    return when {
        days <= 0 -> "Deletes today"
        days == 1 -> "Deletes tomorrow (1 day left)"
        else -> "Auto-deletes in $days days"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val vm: TrashViewModel = viewModel { TrashViewModel(db) }
    val trashedItems by vm.trashedItems.collectAsStateWithLifecycle()

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var folderToDeletePermanently by remember { mutableStateOf<Folder?>(null) }
    var noteToDeletePermanently by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash Bin") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open Sidebar")
                    }
                },
                actions = {
                    if (trashedItems.isNotEmpty()) {
                        TextButton(onClick = { showEmptyTrashDialog = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Empty Trash")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Items in Trash Bin are saved for up to 30 days before being permanently deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (trashedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Trash Bin is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Deleted folders and notes will show up here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(trashedItems, key = { it.id }) { item ->
                        when (item) {
                            is TrashListItem.TrashedFolder -> {
                                val folder = item.folder
                                val details = buildList {
                                    if (item.noteCount > 0) add("${item.noteCount} note${if (item.noteCount == 1) "" else "s"}")
                                    if (item.subfolderCount > 0) add("${item.subfolderCount} subfolder${if (item.subfolderCount == 1) "" else "s"}")
                                }.joinToString(", ")
                                val subtitleText = if (details.isNotBlank()) {
                                    "$details • ${formatDaysRemainingText(item.deletedAt)}"
                                } else {
                                    formatDaysRemainingText(item.deletedAt)
                                }

                                ListItem(
                                    headlineContent = { Text(folder.name) },
                                    supportingContent = { Text(subtitleText) },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = "Folder",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { vm.restoreFolder(folder) }
                                            ) {
                                                Icon(
                                                    Icons.Default.RestoreFromTrash,
                                                    contentDescription = "Restore folder",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { folderToDeletePermanently = folder }
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteForever,
                                                    contentDescription = "Delete permanently",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                            is TrashListItem.TrashedNote -> {
                                val note = item.note
                                ListItem(
                                    headlineContent = { Text(note.title.ifBlank { "Untitled" }) },
                                    supportingContent = { Text(formatDaysRemainingText(item.deletedAt)) },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Description,
                                            contentDescription = "Note",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { vm.restoreNote(note) }
                                            ) {
                                                Icon(
                                                    Icons.Default.RestoreFromTrash,
                                                    contentDescription = "Restore note",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { noteToDeletePermanently = note }
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteForever,
                                                    contentDescription = "Delete permanently",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text("Empty Trash Bin?") },
            text = { Text("All items in the Trash Bin will be permanently deleted. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.emptyTrash()
                        showEmptyTrashDialog = false
                    }
                ) {
                    Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) { Text("Cancel") }
            }
        )
    }

    folderToDeletePermanently?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDeletePermanently = null },
            title = { Text("Delete folder permanently?") },
            text = { Text("\"${folder.name}\" and all items inside it will be permanently deleted. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteFolderPermanently(folder)
                        folderToDeletePermanently = null
                    }
                ) {
                    Text("Delete Permanently", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDeletePermanently = null }) { Text("Cancel") }
            }
        )
    }

    noteToDeletePermanently?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDeletePermanently = null },
            title = { Text("Delete note permanently?") },
            text = { Text("\"${note.title.ifBlank { "Untitled" }}\" will be permanently deleted. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteNotePermanently(note)
                        noteToDeletePermanently = null
                    }
                ) {
                    Text("Delete Permanently", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDeletePermanently = null }) { Text("Cancel") }
            }
        )
    }
}
