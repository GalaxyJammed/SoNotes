package com.example.sonotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE parentId IS :parentId AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeChildren(parentId: Long?): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashedFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getTrashedFoldersSync(): List<Folder>

    @Query("SELECT * FROM folders WHERE parentId IS :parentId")
    suspend fun getChildrenSync(parentId: Long?): List<Folder>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersSync(): List<Folder>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): Folder?

    @Insert
    suspend fun insert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Query("UPDATE folders SET customOrder = :customOrder WHERE id = :id")
    suspend fun updateCustomOrder(id: Long, customOrder: Int)

    @Query("UPDATE folders SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE folders SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM folders WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffTime")
    suspend fun deletePermanentlyOlderThan(cutoffTime: Long)

    @Delete
    suspend fun delete(folder: Folder)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE folderId IS :folderId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeInFolder(folderId: Long?): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND searchText LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getTrashedNotesSync(): List<Note>

    @Query("SELECT * FROM notes WHERE folderId IS :folderId")
    suspend fun getNotesInFolderSync(folderId: Long?): List<Note>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSync(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("UPDATE notes SET customOrder = :customOrder WHERE id = :id")
    suspend fun updateCustomOrder(id: Long, customOrder: Int)

    @Query("UPDATE notes SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffTime")
    suspend fun deletePermanentlyOlderThan(cutoffTime: Long)

    @Delete
    suspend fun delete(note: Note)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY customOrder ASC, name COLLATE NOCASE ASC")
    fun observeAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): Tag?

    @Insert
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: FolderTagCrossRef)

    @Query("DELETE FROM folder_tag_cross_ref WHERE folderId = :folderId AND tagId = :tagId")
    suspend fun deleteCrossRef(folderId: Long, tagId: Long)

    @Query("DELETE FROM folder_tag_cross_ref WHERE tagId = :tagId")
    suspend fun deleteAllCrossRefsForTag(tagId: Long)

    @Query("""
        SELECT f.* FROM folders f
        INNER JOIN folder_tag_cross_ref ref ON f.id = ref.folderId
        WHERE ref.tagId = :tagId AND f.deletedAt IS NULL
        ORDER BY f.name COLLATE NOCASE
    """)
    fun observeFoldersForTag(tagId: Long): Flow<List<Folder>>

    @Query("SELECT * FROM folder_tag_cross_ref")
    fun observeAllCrossRefs(): Flow<List<FolderTagCrossRef>>

    @Transaction
    suspend fun setFoldersForTag(tagId: Long, folderIds: List<Long>) {
        deleteAllCrossRefsForTag(tagId)
        folderIds.forEach { folderId ->
            insertCrossRef(FolderTagCrossRef(folderId = folderId, tagId = tagId))
        }
    }
}