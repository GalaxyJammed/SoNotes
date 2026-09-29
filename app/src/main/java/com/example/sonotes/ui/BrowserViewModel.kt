package com.example.sonotes.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.AppSettings
import com.example.sonotes.data.Folder
import com.example.sonotes.data.Note
import com.example.sonotes.data.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class BrowserListItem {
    abstract val id: String
    abstract val createdAt: Long
    abstract val customOrder: Int

    data class FolderItem(val folder: Folder, val wordCount: Int = 0) : BrowserListItem() {
        override val id: String get() = "f_${folder.id}"
        override val createdAt: Long get() = folder.createdAt
        override val customOrder: Int get() = folder.customOrder
    }

    data class NoteItem(val note: Note, val wordCount: Int = 0) : BrowserListItem() {
        override val id: String get() = "n_${note.id}"
        override val createdAt: Long get() = note.createdAt
        override val customOrder: Int get() = note.customOrder
    }
}

class BrowserViewModel(
    private val db: AppDatabase,
    private val folderId: Long?,
    context: Context
) : ViewModel() {

    var searchQuery by mutableStateOf("")

    private val _sortOption = MutableStateFlow(AppSettings.getSortOption(context))
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _isReorderMode = MutableStateFlow(false)
    val isReorderMode: StateFlow<Boolean> = _isReorderMode.asStateFlow()

    val rawNotes: StateFlow<List<Note>> = if (folderId == null) {
        db.noteDao().search("")
    } else {
        db.noteDao().observeInFolder(folderId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawFolders = db.folderDao().observeChildren(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val displayItems: StateFlow<List<BrowserListItem>> = combine(
        rawFolders,
        rawNotes,
        _sortOption,
        db.noteDao().observeAllNotes(),
        db.folderDao().observeAllFolders()
    ) { currentFolders, currentNotes, sortOption, allNotes, allFolders ->
        val folderWordCounts = currentFolders.associate { folder ->
            folder.id to calculateFolderWordCount(folder.id, allNotes, allFolders)
        }

        when (sortOption) {
            SortOption.DATE_CREATED_NEWEST -> {
                val sortedFolders = currentFolders.sortedByDescending { it.createdAt }
                val sortedNotes = currentNotes.sortedByDescending { it.createdAt }
                sortedFolders.map { BrowserListItem.FolderItem(it, folderWordCounts[it.id] ?: 0) } +
                        sortedNotes.map { BrowserListItem.NoteItem(it, calculateNoteWordCount(it)) }
            }
            SortOption.DATE_CREATED_OLDEST -> {
                val sortedFolders = currentFolders.sortedBy { it.createdAt }
                val sortedNotes = currentNotes.sortedBy { it.createdAt }
                sortedFolders.map { BrowserListItem.FolderItem(it, folderWordCounts[it.id] ?: 0) } +
                        sortedNotes.map { BrowserListItem.NoteItem(it, calculateNoteWordCount(it)) }
            }
            SortOption.ALPHABETICAL_AZ -> {
                val sortedFolders = currentFolders.sortedBy { it.name.lowercase() }
                val sortedNotes = currentNotes.sortedBy { it.title.ifBlank { "Untitled" }.lowercase() }
                sortedFolders.map { BrowserListItem.FolderItem(it, folderWordCounts[it.id] ?: 0) } +
                        sortedNotes.map { BrowserListItem.NoteItem(it, calculateNoteWordCount(it)) }
            }
            SortOption.ALPHABETICAL_ZA -> {
                val sortedFolders = currentFolders.sortedByDescending { it.name.lowercase() }
                val sortedNotes = currentNotes.sortedByDescending { it.title.ifBlank { "Untitled" }.lowercase() }
                sortedFolders.map { BrowserListItem.FolderItem(it, folderWordCounts[it.id] ?: 0) } +
                        sortedNotes.map { BrowserListItem.NoteItem(it, calculateNoteWordCount(it)) }
            }
            SortOption.CONTENT_SIZE_BIGGEST -> {
                val sortedFolders = currentFolders.sortedByDescending { folderWordCounts[it.id] ?: 0 }
                val sortedNotes = currentNotes.sortedByDescending { calculateNoteWordCount(it) }
                sortedFolders.map { BrowserListItem.FolderItem(it, folderWordCounts[it.id] ?: 0) } +
                        sortedNotes.map { BrowserListItem.NoteItem(it, calculateNoteWordCount(it)) }
            }
            SortOption.CONTENT_SIZE_SMALLEST -> {
                val sortedFolders = currentFolders.sortedBy { folderWordCounts[it.id] ?: 0 }
                val sortedNotes = currentNotes.sortedBy { calculateNoteWordCount(it) }
                sortedFolders.map { BrowserListItem.FolderItem(it, folderWordCounts[it.id] ?: 0) } +
                        sortedNotes.map { BrowserListItem.NoteItem(it, calculateNoteWordCount(it)) }
            }
            SortOption.CUSTOM_ORDER -> {
                val combined = currentFolders.map { BrowserListItem.FolderItem(it, folderWordCounts[it.id] ?: 0) as BrowserListItem } +
                        currentNotes.map { BrowserListItem.NoteItem(it, calculateNoteWordCount(it)) as BrowserListItem }
                combined.sortedWith(
                    compareBy<BrowserListItem> { it.customOrder }
                        .thenBy { if (it is BrowserListItem.FolderItem) 0 else 1 }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var title by mutableStateOf("SoNotes - All Notes")
        private set

    init {
        if (folderId != null) {
            viewModelScope.launch {
                db.folderDao().getById(folderId)?.let { title = it.name }
            }
        }
    }

    fun setSortOption(context: Context, option: SortOption) {
        AppSettings.setSortOption(context, option)
        _sortOption.value = option
    }

    fun setReorderMode(enabled: Boolean) {
        _isReorderMode.value = enabled
    }

    fun saveCustomOrder(reorderedItems: List<BrowserListItem>) {
        viewModelScope.launch {
            reorderedItems.forEachIndexed { index, item ->
                when (item) {
                    is BrowserListItem.FolderItem -> db.folderDao().updateCustomOrder(item.folder.id, index)
                    is BrowserListItem.NoteItem -> db.noteDao().updateCustomOrder(item.note.id, index)
                }
            }
        }
    }

    fun addFolder(name: String, parentId: Long? = folderId) {
        viewModelScope.launch {
            db.folderDao().insert(Folder(name = name.trim(), parentId = parentId))
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            softDeleteFolderRecursive(folder.id, System.currentTimeMillis())
        }
    }

    private suspend fun softDeleteFolderRecursive(folderId: Long, timestamp: Long) {
        db.folderDao().softDelete(folderId, timestamp)
        val activeNotes = db.noteDao().getNotesInFolderSync(folderId).filter { it.deletedAt == null }
        activeNotes.forEach { db.noteDao().softDelete(it.id, timestamp) }
        val activeChildFolders = db.folderDao().getChildrenSync(folderId).filter { it.deletedAt == null }
        activeChildFolders.forEach { softDeleteFolderRecursive(it.id, timestamp) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            db.noteDao().softDelete(note.id, System.currentTimeMillis())
        }
    }

    private fun calculateNoteWordCount(note: Note): Int {
        val text = "${note.title} ${note.body}".trim()
        if (text.isEmpty()) return 0
        return text.split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    private fun calculateFolderWordCount(folderId: Long, allNotes: List<Note>, allFolders: List<Folder>): Int {
        val directNotesWordCount = allNotes.filter { it.folderId == folderId }.sumOf { calculateNoteWordCount(it) }
        val childFolders = allFolders.filter { it.parentId == folderId }
        val childFoldersWordCount = childFolders.sumOf { calculateFolderWordCount(it.id, allNotes, allFolders) }
        return directNotesWordCount + childFoldersWordCount
    }
}
