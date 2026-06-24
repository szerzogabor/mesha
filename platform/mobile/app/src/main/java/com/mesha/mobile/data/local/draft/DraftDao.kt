package com.mesha.mobile.data.local.draft

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE syncStatus != 'SYNCED' ORDER BY updatedAt DESC")
    fun observeUnsynced(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun getById(id: String): DraftEntity?

    /** Drafts eligible for a sync attempt (pending or previously failed). */
    @Query("SELECT * FROM drafts WHERE syncStatus IN ('PENDING','FAILED') AND syncAttempts < :maxAttempts ORDER BY createdAt ASC")
    suspend fun getSyncable(maxAttempts: Int): List<DraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Update
    suspend fun update(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM drafts WHERE syncStatus != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>
}
