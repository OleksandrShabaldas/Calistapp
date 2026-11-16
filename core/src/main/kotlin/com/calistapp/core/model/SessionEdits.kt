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

// ---- Reordering a running session ---------------------------------------------------------------

/**
 * A circuit round held to the order it had before a reorder — the "use the new order from next round"
 * answer. [WorkoutPlan.nextUp] honours it only while [round] is the round being worked, so it lapses
 * on its own once that round is done.
 */
@kotlinx.serialization.Serializable
data class RoundOrder(val round: Int, val order: List<String>)

/** What to do with exercises a reorder put behind the one you're on, while they're still owed this round. */
enum class BehindChoice {
    /** Run the rest of this round in its old order; the new order starts next round. */
    NEXT_ROUND,

    /** Keep them in this round: after the exercises ahead, in the new order. */
    LATER_THIS_ROUND,

    /** Skip them this round. */
    SKIP_THIS_ROUND,
}

/**
 * Circuit only: the slots still owed this round that sit *before* [currentSlotId] in the rotation —
 * the ones a reorder can leave "behind" you. Empty unless the round is under way, i.e. the current slot
 * was already worked this round, or is being worked now ([working]).
 */
fun WorkoutPlan.owedBehind(
    currentSlotId: String?,
    completed: Map<String, Int>,
    working: Boolean,
    roundOrder: RoundOrder? = null,
): List<String> {
    if (!isCircuit) return emptyList()
    val rot = rotationFor(roundOrder, completed)
    fun done(id: String) = completed[id] ?: 0
    val owing = rot.filter { targetSetsFor(it.slotId) - done(it.slotId) > 0 }
    if (owing.isEmpty()) return emptyList()
    val lagging = owing.minOf { done(it.slotId) }
    val at = rot.indexOfFirst { it.slotId == currentSlotId }
    if (at < 0) return emptyList()
    val currentDone = done(rot[at].slotId)
    val midRound = currentDone > lagging || (working && currentDone == lagging)
    if (!midRound) return emptyList()
    return rot.take(at).filter { it in owing && done(it.slotId) == lagging }.map { it.slotId }
}

/** How a running session should carry on after a reorder — see [reorderEffect]. */
sealed interface ReorderEffect {
    /**
     * Take the new order. [anchor], when set, is where the session now stands (the slot it continues
     * from); null leaves it where it is. [afterSet] / [afterExercise] defer that re-anchoring in an
     * exercise-by-exercise workout until the set in progress, or the exercise in progress, is finished.
     */
    data class Apply(
        val anchor: NextUp? = null,
        val afterSet: Boolean = false,
        val afterExercise: Boolean = false,
    ) : ReorderEffect

    /** Circuit: the new order leaves [behind] — owed this round — before the current exercise. Ask first. */
    data class AskBehind(val behind: List<String>) : ReorderEffect
}

/**
 * Decide what moving [movedSlotId] (giving [after] from [before]) does to a session standing at
 * [currentSlotId] with [completed] sets resolved.
 *
 * - **Nothing done yet:** the session simply starts from the top of the new order.
 * - **Circuit, mid-round:** if exercises still owed this round end up *behind* the current one (and
 *   weren't already), the user is asked what to do with them ([AskBehind]); otherwise the new order
 *   just takes over from where you are. A round already held to its old order stays held.
 * - **Exercise-by-exercise:** moving the exercise you're on hands over to the first unfinished
 *   exercise in the new order — straight away while resting, after the set in progress while working.
 *   Moving any other exercise keeps you on the current one until it's finished, then carries on from
 *   the first unfinished exercise in the new order (so one moved above you comes next).
 */
fun reorderEffect(
    before: WorkoutPlan,
    after: WorkoutPlan,
    movedSlotId: String,
    currentSlotId: String?,
    completed: Map<String, Int>,
    deferred: Set<String>,
    working: Boolean,
    roundOrder: RoundOrder? = null,
): ReorderEffect {
    if (completed.values.sum() == 0 && !working) {
        return ReorderEffect.Apply(anchor = after.nextUp(null, completed, deferred))
    }
    if (after.isCircuit) {
        if (roundOrder != null && roundOrder.round == after.roundOf(completed)) return ReorderEffect.Apply()
        val was = before.owedBehind(currentSlotId, completed, working).toSet()
        val now = after.owedBehind(currentSlotId, completed, working)
        val newlyBehind = now.filter { it !in was }
        return if (newlyBehind.isEmpty()) ReorderEffect.Apply() else ReorderEffect.AskBehind(newlyBehind)
    }
    // Exercise-by-exercise. Supersets and the warm-up keep their own rotation rules.
    val current = after.slot(currentSlotId) ?: return ReorderEffect.Apply()
    if (current.groupId != null || after.phaseOf(current) != SlotPhase.MAIN) return ReorderEffect.Apply()
    val currentOwes = after.targetSetsFor(current.slotId) - (completed[current.slotId] ?: 0) > 0
    return when {
        movedSlotId == current.slotId && working -> ReorderEffect.Apply(afterSet = true)
        movedSlotId == current.slotId || !currentOwes && !working ->
            ReorderEffect.Apply(anchor = after.nextUp(null, completed, deferred))
        else -> ReorderEffect.Apply(afterExercise = true)
    }
}
