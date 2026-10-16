package com.calistapp.app.data.session

import com.calistapp.app.data.local.WorkoutTemplateDao
import com.calistapp.app.data.local.WorkoutTemplateEntity
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Exercise
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlanSelectionTest {
    @Test fun `deselect removes every duplicate and preserves other movement settings`() = runTest {
        val saved = SavedWorkoutRepository(object : WorkoutTemplateDao {
            override fun observeAll() = flowOf(emptyList<WorkoutTemplateEntity>())
            override suspend fun upsert(template: WorkoutTemplateEntity) = Unit
            override suspend fun updateContent(id: String, name: String, planJson: String) = Unit
            override suspend fun markUsed(id: String, usedMs: Long) = Unit
            override suspend fun delete(id: String) = Unit
        }, Json)
        val draft = PlanDraftRepository(backgroundScope, saved)
        val pull = Exercise("pull", "Pull-up", BodyPart.BACK)
        val push = Exercise("push", "Push-up", BodyPart.CHEST)
        draft.add(pull)
        draft.add(pull)
        draft.add(push)
        val pushSlot = draft.draft.value.exercises.last()
        draft.update(pushSlot.slotId) { it.copy(targetSets = 5, metabolics = it.metabolics.copy(externalLoadKg = 12.0)) }
        val expectedPush = draft.draft.value.exercises.last()
        assertFalse(draft.toggleSelection(pull))
        assertEquals(listOf(expectedPush), draft.draft.value.exercises)
        assertTrue(draft.toggleSelection(pull))
        assertEquals(1, draft.draft.value.exercises.count { it.exerciseId == "pull" })
        assertEquals(expectedPush, draft.draft.value.exercises.first())
        assertFalse(draft.toggleSelection(pull))
        assertFalse(draft.toggleSelection(push))
        assertTrue(draft.draft.value.isEmpty)
    }
}
