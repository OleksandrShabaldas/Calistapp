package com.calistapp.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A gallery exercise. Filterable columns are promoted out of the JSON so the list can be queried
 * cheaply; the full rich [com.calistapp.core.model.Exercise] lives in [json].
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bodyPart: String,
    val difficulty: String,
    val isBodyweight: Boolean,
    val isCalisthenics: Boolean,
    val json: String,
)
