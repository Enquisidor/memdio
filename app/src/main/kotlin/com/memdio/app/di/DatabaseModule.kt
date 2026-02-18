package com.memdio.app.di

import android.content.Context
import androidx.room.Room
import com.memdio.app.data.db.AppDatabase
import com.memdio.app.data.db.ChunkDao
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
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "memdio.db")
            .fallbackToDestructiveMigration() // V1: no migrations needed
            .build()

    @Provides
    fun provideChunkDao(db: AppDatabase): ChunkDao = db.chunkDao()
}
