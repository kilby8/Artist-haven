package com.artisthaven.app.di

import android.content.Context
import androidx.room.Room
import com.artisthaven.app.data.local.AppDatabase
import com.artisthaven.app.data.local.dao.LayerDao
import com.artisthaven.app.data.local.dao.ProjectDao
import com.artisthaven.app.data.repository.ProjectRepositoryImpl
import com.artisthaven.app.domain.command.CommandHistory
import com.artisthaven.app.domain.repository.ProjectRepository
import dagger.Binds
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "artist_haven_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideLayerDao(db: AppDatabase): LayerDao = db.layerDao()

    @Provides
    @Singleton
    fun provideCommandHistory(): CommandHistory = CommandHistory(maxSize = 50)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository
}
