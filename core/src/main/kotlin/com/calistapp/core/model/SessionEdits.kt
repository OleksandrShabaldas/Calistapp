package com.calistapp.core.model

/*
 * Edits to a running session's own copy of its plan — removing, adding, reordering and retargeting
 * exercises for today only. The saved workout is never touched here; [savableOver] and
 * [describeChanges] are what the end-of-session "save these changes?" prompt is built from.
 */

/** Append [slot] at the end of the workout block, ahead of any cool-down. */
fun WorkoutPlan.withAddedSlot(slot: PlannedExercise): WorkoutPlan {
    val firstCool = exercises.indexOfFirst { phaseOf(it) == SlotPhase.COOL_DOWN }
    val list = exercises.toMutableList()
    if (firstCool < 0) list += slot else list.add(firstCool, slot)
    return copy(exercises = list)
}

/** Drop [slotId] outright — only for a slot with nothing logged or skipped against it. */
fun WorkoutPlan.withoutSlot(slotId: String): WorkoutPlan =
    copy(exercises = exercises.filterNot { it.slotId == slotId })

/** Move [slotId] to position [toIndex] among the slots of its own phase — a stretch can't land mid-workout. */
fun WorkoutPlan.withSlotMoved(slotId: String, toIndex: Int): WorkoutPlan {
    val slot = slot(slotId) ?: return this
    val phase = phaseOf(slot)
    val inPhase = slotsIn(phase).toMutableList()
    val from = inPhase.indexOfFirst { it.slotId == slotId }
    val to = toIndex.coerceIn(0, inPhase.lastIndex)
    if (from == to) return this
    inPhase.add(to, inPhase.removeAt(from))
    // The phase's positions in the list stay where they are; only its own order changes.
    val reordered = inPhase.iterator()
    return copy(exercises = exercises.map { if (phaseOf(it) == phase) reordered.next() else it })
}

/**
 * Change what [slotId]'s remaining sets ask for, from set [fromSetIndex] on: the target (reps, or
 * seconds for a hold) and the added load. Sets already done keep their own planned values, so the
 * record of what was asked of them doesn't shift underneath their logs.
 */
fun WorkoutPlan.withTarget(slotId: String, fromSetIndex: Int, value: Int, weightKg: Double): WorkoutPlan {
    val v = value.coerceAtLeast(1)
    val kg = weightKg.coerceIn(0.0, 300.0)
    return copy(
        exercises = exercises.map { slot ->
            if (slot.slotId != slotId) return@map slot
            val perSet = slot.plannedSets.takeIf { it.isNotEmpty() }
                ?.mapIndexed { i, s -> if (i >= fromSetIndex - 1) s.copy(reps = v, weightKg = kg) else s }
                ?: slot.plannedSets
            slot.copy(
                targetReps = if (slot.measure == ExerciseMeasure.REPS) v else slot.targetReps,
                targetSeconds = if (slot.measure == ExerciseMeasure.SECONDS) v else slot.targetSeconds,
                plannedSets = perSet,
                metabolics = slot.metabolics.copy(externalLoadKg = kg),
            )
        },
    )
}

/**
 * The saved workout as this session left it: [original]'s own settings with the session's workout
 * block — its order and targets, exercises added mid-session, minus the [removed] ones — and never
 * the warm-up or cool-down, which are picked per session on the setup screen.
 */
fun WorkoutPlan.savableOver(original: WorkoutPlan, removed: Set<String>): WorkoutPlan =
    original.copy(
        exercises = slotsIn(SlotPhase.MAIN).filterNot { it.slotId in removed },
        rounds = rounds,
    )

/**
 * What changed between [original] and the session's [edited] version, one line each — empty when
 * nothing did, which is the signal not to ask at all. Compared on what each plan would *print*
 * ([WorkoutPlan.targetLabelFor]), so a no-op edit never reads as a change.
 */
fun describeChanges(original: WorkoutPlan, edited: WorkoutPlan): List<String> = buildList {
    val before = original.exercises.associateBy { it.slotId }
    val after = edited.exercises.associateBy { it.slotId }
    original.exercises.filter { it.slotId !in after }.forEach { add("Removed ${it.name}") }
    edited.exercises.filter { it.slotId !in before }.forEach { add("Added ${it.name}") }
    val kept = edited.exercises.filter { it.slotId in before }.map { it.slotId }
    if (kept != original.exercises.filter { it.slotId in after }.map { it.slotId }) add("New exercise order")
    // Per-round targets, so a changed round count reads as one line below rather than as a change
    // to every exercise in the circuit.
    val oneRoundBefore = original.copy(rounds = 1)
    val oneRoundAfter = edited.copy(rounds = 1)
    edited.exercises.forEach { slot ->
        val old = before[slot.slotId] ?: return@forEach
        val was = oneRoundBefore.targetLabelFor(old)
        val now = oneRoundAfter.targetLabelFor(slot)
        if (was != now) add("${slot.name}: $was → $now")
    }
    if (original.isCircuit && original.rounds != edited.rounds) {
        add("Rounds: ${original.rounds} → ${edited.rounds}")
    }
}
