package com.musheer360.swiftslate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CommandEntity::class,
        ProviderEntity::class,
        KeyEntity::class,
        CommandOverrideEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commandDao(): CommandDao
    abstract fun providerDao(): ProviderDao
    abstract fun keyDao(): KeyDao
    abstract fun commandOverrideDao(): CommandOverrideDao
}
