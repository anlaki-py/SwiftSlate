package com.musheer360.swiftslate.di

import com.musheer360.swiftslate.data.repository.CommandRepository
import com.musheer360.swiftslate.data.repository.CommandRepositoryImpl
import com.musheer360.swiftslate.data.repository.KeyRepository
import com.musheer360.swiftslate.data.repository.KeyRepositoryImpl
import com.musheer360.swiftslate.data.repository.ProviderRepository
import com.musheer360.swiftslate.data.repository.ProviderRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCommandRepository(impl: CommandRepositoryImpl): CommandRepository

    @Binds
    @Singleton
    abstract fun bindKeyRepository(impl: KeyRepositoryImpl): KeyRepository

    @Binds
    @Singleton
    abstract fun bindProviderRepository(impl: ProviderRepositoryImpl): ProviderRepository
}
