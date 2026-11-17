package com.calistapp.core.medals

import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.WeatherSnapshot
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.progress.PerformedSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Shared builders for the medal tests: sessions read like a training log, not like constructors. */
internal object MedalTestKit {

    val zone: ZoneId = ZoneId.of("Europe/Bratislava")

    fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** One logged set. A positive [seconds] makes it a timed hold. */
    data class SetSpec(
        val name: String,
        val reps: Int = 0,
        val seconds: Int = 0,
        val kg: Double = 0.0,
        val id: String = name,
        val warmup: Boolean = false,
        val phase: SlotPhase = SlotPhase.MAIN,
        /** Load on the plan slot only — how older logs, written before per-set weight, carried it. */
        val slotKg: Double = 0.0,
        /** A hold logged the old way: seconds only in the reps field, known by the slot's measure. */
        val legacyHold: Boolean = false,
    )

    fun set(
        name: String,
        reps: Int = 0,
        seconds: Int = 0,
        kg: Double = 0.0,
        id: String = name,
        warmup: Boolean = false,
        phase: SlotPhase = SlotPhase.MAIN,
        slotKg: Double = 0.0,
        legacyHold: Boolean = false,
    ) = SetSpec(name, reps, seconds, kg, id, warmup, phase, slotKg, legacyHold)

    fun session(
        id: String,
        start: String,
        vararg sets: SetSpec,
        avgHr: Int = 0,
        hrCoverage: Double = 1.0,
        estimated: Boolean = false,
        zoneMinutes: IntArray = IntArray(0),
        recovery: Int? = null,
        weather: WeatherSnapshot? = null,
        outdoor: Boolean? = null,
        planName: String = "",
        sourceWorkoutId: String? = null,
        totalMinutes: Int = 45,
        activeMinutes: Int = 15,
    ): PerformedSession {
        val startMs = at(start)
        val slots = sets.mapIndexed { i, s ->
            PlannedExercise(
                slotId = "$id-slot-$i",
                exerciseId = s.id,
                name = s.name,
                measure = if (s.seconds > 0) ExerciseMeasure.SECONDS else ExerciseMeasure.REPS,
                warmupSets = if (s.warmup) 1 else 0,
                metabolics = ExerciseMetabolics(externalLoadKg = s.slotKg),
                phase = s.phase,
                runsOnce = s.phase != SlotPhase.MAIN,
            )
        }
        val logs = sets.mapIndexed { i, s ->
            SetLog(
                slotId = "$id-slot-$i",
                exerciseId = s.id,
                exerciseName = s.name,
                setIndex = 1,
                reps = if (s.seconds > 0) s.seconds else s.reps,
                seconds = if (s.seconds > 0 && !s.legacyHold) s.seconds else 0,
                startMs = startMs + i * 60_000L,
                endMs = startMs + i * 60_000L + 40_000L,
                weightKg = s.kg,
            )
        }
        return PerformedSession(
            id = id,
            startMs = startMs,
            kcal = 200,
            activeDurationMs = activeMinutes * 60_000L,
            setLogs = logs,
            plan = WorkoutPlan(id = "plan-$id", name = planName, exercises = slots, sourceWorkoutId = sourceWorkoutId),
            avgHr = avgHr,
            totalDurationMs = totalMinutes * 60_000L,
            hrRecoveryMeanDrop = recovery,
            zoneMinutes = zoneMinutes,
            hrCoverage = hrCoverage,
            estimated = estimated,
            weather = weather,
            outdoor = outdoor,
        )
    }

    fun exercise(id: String, name: String = id, muscles: List<String> = emptyList(), force: String? = null) =
        Exercise(id = id, name = name, bodyPart = BodyPart.OTHER, primaryMuscles = muscles, force = force)

    fun evaluate(
        sessions: List<PerformedSession>,
        exercises: List<Exercise> = emptyList(),
        plannedDays: Set<LocalDate> = emptySet(),
        now: String? = null,
        zone: ZoneId = MedalTestKit.zone,
    ): MedalReport = MedalEngine.evaluate(
        sessions = sessions,
        exercises = exercises.associateBy { it.id },
        plannedDays = plannedDays,
        zone = zone,
        nowMs = now?.let(::at),
    )

    fun MedalReport.of(id: String): MedalLine = requireNotNull(line(id)) { "no line $id" }
}
