package com.example.sonotes.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.sonotes.data.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    folderId: Long?,
    isParentFolder: Boolean,
    onOpenFolder: (Long, Boolean) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenDrawer: () -> Unit,
    onBack: (() -> Unit)?
) {
    val context = LocalContext.current
    val vm: BrowserViewModel = viewModel(key = "browser_$folderId") {
        BrowserViewModel(AppDatabase.get(context), folderId, context)
    }
    val displayItems by vm.displayItems.collectAsStateWithLifecycle()
    val sortOption by vm.sortOption.collectAsStateWithLifecycle()
    val isReorderMode by vm.isReorderMode.collectAsStateWithLifecycle()

    var reorderedItems by remember { mutableStateOf(displayItems) }
    LaunchedEffect(displayItems, isReorderMode) {
        if (!isReorderMode) {
            reorderedItems = displayItems
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isReorderMode) "Reorder Items" else vm.title)
                },
                navigationIcon = {
                    if (isParentFolder) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Sidebar")
                        }
                    } else {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                },
                actions = {
                    if (isReorderMode) {
                        IconButton(onClick = {
                            vm.saveCustomOrder(reorderedItems)
                            vm.setReorderMode(false)
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Lock Order")
                        }
                    } else {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort and Filter")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isReorderMode) {
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
        }
    ) { padding ->
        FolderOrNoteContent(
            padding = padding,
            items = if (isReorderMode) reorderedItems else displayItems,
            sortOption = sortOption,
            isReorderMode = isReorderMode,
            onReorderedItemsChanged = { reorderedItems = it },
            onOpenFolder = { id -> onOpenFolder(id, folderId == null) },
            onOpenNote = onOpenNote,
            onDeleteFolder = { folderToDelete = it },
            onDeleteNote = { noteToDelete = it }
        )
    }

    if (showFilterDialog) {
        FilterSortDialog(
            currentOption = sortOption,
            onOptionSelected = { option ->
                vm.setSortOption(context, option)
                showFilterDialog = false
                if (option == SortOption.CUSTOM_ORDER) {
                    reorderedItems = displayItems
                    vm.setReorderMode(true)
                } else {
                    vm.setReorderMode(false)
                }
            },
            onDismiss = { showFilterDialog = false }
        )
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
            title = { Text("Move to Trash Bin?") },
            text = { Text("\"${folder.name}\" and its contents will be moved to the Trash Bin. Items are kept for up to 30 days before being permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolder(folder)
                    folderToDelete = null
                }) { Text("Move to Trash") }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Cancel") }
            }
        )
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Move to Trash Bin?") },
            text = { Text("\"${note.title.ifBlank { "Untitled" }}\" will be moved to the Trash Bin. Items are kept for up to 30 days before being permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNote(note)
                    noteToDelete = null
                }) { Text("Move to Trash") }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun FilterSortDialog(
    currentOption: SortOption,
    onOptionSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Date Created", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                SortOptionRow(
                    label = "Newest to Oldest",
                    selected = currentOption == SortOption.DATE_CREATED_NEWEST,
                    onClick = { onOptionSelected(SortOption.DATE_CREATED_NEWEST) }
                )
                SortOptionRow(
                    label = "Oldest to Newest",
                    selected = currentOption == SortOption.DATE_CREATED_OLDEST,
                    onClick = { onOptionSelected(SortOption.DATE_CREATED_OLDEST) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Alphabetical", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                SortOptionRow(
                    label = "A to Z",
                    selected = currentOption == SortOption.ALPHABETICAL_AZ,
                    onClick = { onOptionSelected(SortOption.ALPHABETICAL_AZ) }
                )
                SortOptionRow(
                    label = "Z to A",
                    selected = currentOption == SortOption.ALPHABETICAL_ZA,
                    onClick = { onOptionSelected(SortOption.ALPHABETICAL_ZA) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Content Size", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                SortOptionRow(
                    label = "Biggest to Smallest",
                    selected = currentOption == SortOption.CONTENT_SIZE_BIGGEST,
                    onClick = { onOptionSelected(SortOption.CONTENT_SIZE_BIGGEST) }
                )
                SortOptionRow(
                    label = "Smallest to Biggest",
                    selected = currentOption == SortOption.CONTENT_SIZE_SMALLEST,
                    onClick = { onOptionSelected(SortOption.CONTENT_SIZE_SMALLEST) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Custom Order", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                SortOptionRow(
                    label = "Custom Order (Drag to reorder)",
                    selected = currentOption == SortOption.CUSTOM_ORDER,
                    onClick = { onOptionSelected(SortOption.CUSTOM_ORDER) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SortOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun FolderOrNoteContent(
    padding: PaddingValues,
    items: List<BrowserListItem>,
    sortOption: SortOption,
    isReorderMode: Boolean,
    onReorderedItemsChanged: (List<BrowserListItem>) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onDeleteNote: (Note) -> Unit
) {
    val showDate = sortOption == SortOption.DATE_CREATED_NEWEST || sortOption == SortOption.DATE_CREATED_OLDEST

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in items.indices && toIndex in items.indices && fromIndex != toIndex) {
            val list = items.toMutableList()
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            onReorderedItemsChanged(list)
        }
    }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Nothing here yet. Tap + to add a folder or note.")
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                when (item) {
                    is BrowserListItem.FolderItem -> {
                        val folder = item.folder
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Icon(Icons.Default.Folder, null) },
                            trailingContent = {
                                if (isReorderMode) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { moveItem(index, index - 1) },
                                            enabled = index > 0
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                                        }
                                        IconButton(
                                            onClick = { moveItem(index, index + 1) },
                                            enabled = index < items.lastIndex
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                                        }
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Drag Handle",
                                            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (showDate) {
                                            Text(
                                                text = formatSimplifiedDate(folder.createdAt),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }
                                        IconButton(onClick = { onDeleteFolder(folder) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete folder")
                                        }
                                    }
                                }
                            },
                            modifier = if (!isReorderMode) Modifier.clickable { onOpenFolder(folder.id) } else Modifier
                        )
                    }
                    is BrowserListItem.NoteItem -> {
                        val note = item.note
                        ListItem(
                            headlineContent = { Text(note.title.ifBlank { "Untitled" }) },
                            leadingContent = { Icon(Icons.Default.Description, null) },
                            trailingContent = {
                                if (isReorderMode) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { moveItem(index, index - 1) },
                                            enabled = index > 0
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                                        }
                                        IconButton(
                                            onClick = { moveItem(index, index + 1) },
                                            enabled = index < items.lastIndex
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                                        }
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Drag Handle",
                                            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (showDate) {
                                            Text(
                                                text = formatSimplifiedDate(note.createdAt),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }
                                        IconButton(onClick = { onDeleteNote(note) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete note")
                                        }
                                    }
                                }
                            },
                            modifier = if (!isReorderMode) Modifier.clickable { onOpenNote(note.id) } else Modifier
                        )
                    }
                }
            }
        }
    }
}

private fun formatSimplifiedDate(timestampMs: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}
