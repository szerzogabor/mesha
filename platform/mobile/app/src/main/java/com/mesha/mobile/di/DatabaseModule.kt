package com.mesha.mobile.di

import android.content.Context
import androidx.room.Room
import com.mesha.mobile.data.local.MeshaDatabase
import com.mesha.mobile.data.local.draft.DraftDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeshaDatabase =
        Room.databaseBuilder(context, MeshaDatabase::class.java, MeshaDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDraftDao(database: MeshaDatabase): DraftDao = database.draftDao()
}
