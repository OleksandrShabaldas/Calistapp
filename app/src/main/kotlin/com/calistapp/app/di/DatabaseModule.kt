package com.calistapp.app.di

import android.content.Context
import androidx.room.Room
import com.calistapp.app.data.local.CalistDatabase
import com.calistapp.app.data.local.ExerciseDao
import com.calistapp.app.data.local.MIGRATION_3_4
import com.calistapp.app.data.local.MIGRATION_4_5
import com.calistapp.app.data.local.MIGRATION_5_6
import com.calistapp.app.data.local.MIGRATION_6_7
import com.calistapp.app.data.local.MIGRATION_7_8
import com.calistapp.app.data.local.SessionDao
import com.calistapp.app.data.local.StepDayDao
import com.calistapp.app.data.local.WeightDao
import com.calistapp.app.data.local.WorkoutTemplateDao
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
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            // Still a backstop for the older dev-only schema jumps that never had migrations.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(db: CalistDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideExerciseDao(db: CalistDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideWorkoutTemplateDao(db: CalistDatabase): WorkoutTemplateDao = db.workoutTemplateDao()

    @Provides
    fun provideWeightDao(db: CalistDatabase): WeightDao = db.weightDao()

    @Provides
    fun provideStepDayDao(db: CalistDatabase): StepDayDao = db.stepDayDao()
}
