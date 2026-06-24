package com.mesha.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mesha.mobile.data.local.draft.DraftDao
import com.mesha.mobile.data.local.draft.DraftEntity

@Database(
    entities = [DraftEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MeshaDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao

    companion object {
        const val NAME = "mesha.db"
    }
}
