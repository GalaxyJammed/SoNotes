package com.example.sonotes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.Folder
import com.example.sonotes.data.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowserViewModel(private val db: AppDatabase, private val folderId: Long?) : ViewModel() {

    var searchQuery by mutableStateOf("")

    val notes: StateFlow<List<Note>> = if (folderId == null) {
        db.noteDao().search("")
    } else {
        db.noteDao().observeInFolder(folderId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFolders = db.folderDao().observeChildren(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders = db.folderDao().observeChildren(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var title by mutableStateOf("SoNotes - All Notes")
        private set

    init {
        if (folderId != null) {
            viewModelScope.launch {
                db.folderDao().getById(folderId)?.let { title = it.name }
            }
        }
    }

    fun addFolder(name: String, parentId: Long? = folderId) {
        viewModelScope.launch {
            db.folderDao().insert(Folder(name = name.trim(), parentId = parentId))
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch { db.folderDao().delete(folder) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { db.noteDao().delete(note) }
    }
}
