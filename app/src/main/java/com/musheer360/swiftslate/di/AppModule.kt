package com.musheer360.swiftslate.di

import android.content.Context
import androidx.room.Room
import com.musheer360.swiftslate.data.local.AppDatabase
import com.musheer360.swiftslate.data.remote.ApiServiceFactory
import com.musheer360.swiftslate.data.remote.OpenAiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "swiftslate.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideApiServiceFactory(json: Json): ApiServiceFactory {
        return ApiServiceFactory(json)
    }

    @Provides
    @Singleton
    fun provideOpenAiClient(json: Json, apiServiceFactory: ApiServiceFactory): OpenAiClient {
        return OpenAiClient(json, apiServiceFactory)
    }
}
