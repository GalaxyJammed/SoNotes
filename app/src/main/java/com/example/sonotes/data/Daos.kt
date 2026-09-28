package com.example.sonotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE parentId IS :parentId ORDER BY name COLLATE NOCASE")
    fun observeChildren(parentId: Long?): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): Folder?

    @Insert
    suspend fun insert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE folderId IS :folderId ORDER BY updatedAt DESC")
    fun observeInFolder(folderId: Long?): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE searchText LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}