package com.calistapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These values feed Keytel and Mifflin–St Jeor directly, so a bad one doesn't make the calorie
 * estimate slightly off — it makes it confidently wrong. The point of validation here is to catch
 * typos and empty fields, not to be strict for its own sake.
 */
class UserProfileValidationTest {

    private val valid = UserProfile(
        sex = Sex.MALE, ageYears = 30, weightKg = 75.0, heightCm = 178.0, restingHr = 55,
    )

    @Test
    fun `a filled-in profile is valid`() {
        assertTrue(valid.isValid)
        assertEquals(emptySet<ProfileField>(), valid.invalidFields())
    }

    @Test
    fun `optional fields left unset are not errors`() {
        val bare = valid.copy(maxHr = null, vo2Max = null)

        assertTrue(bare.isValid)
    }

    @Test
    fun `optional fields that are set still have to be plausible`() {
        assertEquals(setOf(ProfileField.MAX_HR), valid.copy(maxHr = 400).invalidFields())
        assertEquals(setOf(ProfileField.VO2_MAX), valid.copy(vo2Max = 300.0).invalidFields())
    }

    @Test
    fun `a max heart rate at or below resting is rejected as a pair`() {
        val backwards = valid.copy(restingHr = 120, maxHr = 110)

        assertEquals(
            setOf(ProfileField.MAX_HR, ProfileField.RESTING_HR),
            backwards.invalidFields(),
        )
    }

    @Test
    fun `the sentinel an empty form field parses to is rejected, not accepted as a default`() {
        // This is the regression that mattered: a blank weight used to silently become 75 kg.
        val blank = UserProfile(ageYears = -1, weightKg = -1.0, heightCm = -1.0, restingHr = -1)

        assertFalse(blank.isValid)
        assertEquals(
            setOf(
                ProfileField.AGE,
                ProfileField.WEIGHT,
                ProfileField.HEIGHT,
                ProfileField.RESTING_HR,
            ),
            blank.invalidFields(),
        )
    }

    @Test
    fun `each field is reported independently`() {
        assertEquals(setOf(ProfileField.WEIGHT), valid.copy(weightKg = 7.0).invalidFields())
        assertEquals(setOf(ProfileField.HEIGHT), valid.copy(heightCm = 20.0).invalidFields())
        assertEquals(setOf(ProfileField.AGE), valid.copy(ageYears = 200).invalidFields())
    }

    @Test
    fun `boundaries are inclusive`() {
        assertTrue(valid.copy(weightKg = 20.0).isValid)
        assertTrue(valid.copy(weightKg = 400.0).isValid)
        assertTrue(valid.copy(ageYears = 5).isValid)
        assertTrue(valid.copy(ageYears = 120).isValid)
        assertFalse(valid.copy(weightKg = 19.9).isValid)
        assertFalse(valid.copy(ageYears = 121).isValid)
    }

    @Test
    fun `the default profile is usable out of the box`() {
        // A new install must not open in an invalid state, or Save is dead on arrival.
        assertTrue(UserProfile().isValid)
    }
}
