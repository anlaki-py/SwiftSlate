package com.musheer360.swiftslate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "api_keys",
    indices = [Index(value = ["providerId"])]
)
data class KeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val providerId: String,
    val encryptedKey: String
)
