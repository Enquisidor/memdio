package com.memdio.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChunkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
}
