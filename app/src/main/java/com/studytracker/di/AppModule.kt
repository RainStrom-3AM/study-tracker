package com.studytracker.di

import android.content.Context
import androidx.room.Room
import com.studytracker.data.db.StudyDatabase
import com.studytracker.data.db.dao.SessionDao
import com.studytracker.data.db.dao.SettingsDao
import com.studytracker.data.db.dao.SubjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StudyDatabase {
        return Room.databaseBuilder(
            context,
            StudyDatabase::class.java,
            StudyDatabase.DATABASE_NAME
        )
            .addCallback(StudyDatabase.createCallback())
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSessionDao(database: StudyDatabase): SessionDao = database.sessionDao()

    @Provides
    @Singleton
    fun provideSubjectDao(database: StudyDatabase): SubjectDao = database.subjectDao()

    @Provides
    @Singleton
    fun provideSettingsDao(database: StudyDatabase): SettingsDao = database.settingsDao()
}
