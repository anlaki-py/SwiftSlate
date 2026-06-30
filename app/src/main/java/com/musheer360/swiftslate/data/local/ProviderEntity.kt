package com.musheer360.swiftslate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.musheer360.swiftslate.model.Provider

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val endpoint: String,
    val selectedModel: String = "",
    val isActive: Boolean = false
)

fun ProviderEntity.toDomain(): Provider = Provider(
    id = id,
    name = name,
    endpoint = endpoint,
    selectedModel = selectedModel
)

fun Provider.toEntity(isActive: Boolean = false): ProviderEntity = ProviderEntity(
    id = id,
    name = name,
    endpoint = endpoint,
    selectedModel = selectedModel,
    isActive = isActive
)
