package com.calistapp.app.di

import android.content.Context
import androidx.room.Room
import com.calistapp.app.data.local.CalistDatabase
import com.calistapp.app.data.local.ExerciseDao
import com.calistapp.app.data.local.MIGRATION_3_4
import com.calistapp.app.data.local.SessionDao
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
    fun provideDatabase(@ApplicationContext context: Context): CalistDatabase =
        Room.databaseBuilder(context, CalistDatabase::class.java, "calistapp.db")
            .addMigrations(MIGRATION_3_4)
            // Still a backstop for the older dev-only schema jumps that never had migrations.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(db: CalistDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideExerciseDao(db: CalistDatabase): ExerciseDao = db.exerciseDao()
}
