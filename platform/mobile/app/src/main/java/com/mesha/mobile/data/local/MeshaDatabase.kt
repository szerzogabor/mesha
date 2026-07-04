package com.mesha.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mesha.mobile.data.local.chat.ChatMessageDao
import com.mesha.mobile.data.local.chat.ChatMessageEntity
import com.mesha.mobile.data.local.draft.DraftDao
import com.mesha.mobile.data.local.draft.DraftEntity

@Database(
    entities = [DraftEntity::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MeshaDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        const val NAME = "mesha.db"
    }
}
