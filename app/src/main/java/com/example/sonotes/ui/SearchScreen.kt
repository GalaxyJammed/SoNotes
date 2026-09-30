package com.example.sonotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.Folder
import com.example.sonotes.data.Note
import com.example.sonotes.data.normalizeForSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed class SearchResultItem {
    abstract val id: String
    data class FolderResult(val folder: Folder) : SearchResultItem() {
        override val id: String get() = "f_${folder.id}"
    }
    data class NoteResult(val note: Note) : SearchResultItem() {
        override val id: String get() = "n_${note.id}"
    }
}

enum class SearchMode {
    NAMES,
    CONTENT
}

class SearchViewModel(db: AppDatabase) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.NAMES)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
    }

    private val allFolders: StateFlow<List<Folder>> = db.folderDao().observeAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allNotes: StateFlow<List<Note>> = db.noteDao().observeAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults: StateFlow<List<SearchResultItem>> = combine(allFolders, allNotes, _searchQuery, _searchMode) { folders, notes, queryParam, mode ->
        val query = queryParam.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            val normalizedQuery = normalizeForSearch(query)
            when (mode) {
                SearchMode.NAMES -> {
                    val matchedFolders = folders.filter { folder ->
                        normalizeForSearch(folder.name).contains(normalizedQuery)
                    }.map { SearchResultItem.FolderResult(it) }

                    val matchedNotes = notes.filter { note ->
                        normalizeForSearch(note.title).contains(normalizedQuery)
                    }.map { SearchResultItem.NoteResult(it) }

                    matchedFolders + matchedNotes
                }
                SearchMode.CONTENT -> {
                    val matchedNotes = notes.filter { note ->
                        normalizeForSearch(note.body).contains(normalizedQuery)
                    }.map { SearchResultItem.NoteResult(it) }

                    matchedNotes
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenNote: (Long, Long?) -> Unit
) {
    val context = LocalContext.current
    val vm: SearchViewModel = viewModel {
        SearchViewModel(AppDatabase.get(context))
    }
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val searchMode by vm.searchMode.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()

    var showModeDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.updateSearchQuery(it) },
                        placeholder = { Text(if (searchMode == SearchMode.NAMES) "Search names..." else "Search note content...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .focusRequester(focusRequester),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { showModeDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Search Filter Mode")
                    }
                }
            )
        }
    ) { padding ->
        if (query.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Type to search (${if (searchMode == SearchMode.NAMES) "Folder/File Names" else "Note Content"})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No results found for \"$query\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(results, key = { it.id }) { item ->
                    when (item) {
                        is SearchResultItem.FolderResult -> {
                            ListItem(
                                headlineContent = { Text(item.folder.name) },
                                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                supportingContent = { Text("Folder") },
                                modifier = Modifier.clickable {
                                    onOpenFolder(item.folder.id)
                                }
                            )
                        }
                        is SearchResultItem.NoteResult -> {
                            ListItem(
                                headlineContent = { Text(item.note.title.ifBlank { "Untitled" }) },
                                leadingContent = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                                supportingContent = { Text("Note") },
                                modifier = Modifier.clickable {
                                    onOpenNote(item.note.id, item.note.folderId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showModeDialog) {
        SearchModeDialog(
            currentMode = searchMode,
            onModeSelected = { mode ->
                vm.setSearchMode(mode)
                showModeDialog = false
            },
            onDismiss = { showModeDialog = false }
        )
    }
}

@Composable
fun SearchModeDialog(
    currentMode: SearchMode,
    onModeSelected: (SearchMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search in") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SearchModeRow(
                    label = "Folder/File Names",
                    selected = currentMode == SearchMode.NAMES,
                    onClick = { onModeSelected(SearchMode.NAMES) }
                )
                SearchModeRow(
                    label = "Note Content",
                    selected = currentMode == SearchMode.CONTENT,
                    onClick = { onModeSelected(SearchMode.CONTENT) }
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
fun SearchModeRow(
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
