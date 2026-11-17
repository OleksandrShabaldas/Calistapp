package com.calistapp.core.medals

import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.isUnilateral

/**
 * A movement a medal is about, recognised by name.
 *
 * The gallery has no single id per movement: the same pull-up exists as free-exercise-db's `Pullups`,
 * an authored `Pull-Up`, a video-library `Ring Pull-Up` and a plate-weighted variant, and a medal must
 * count all of them while refusing the regressions (assisted, band, negative, knee, bench) that would
 * hand it out for an easier movement. So each movement is a required word plus words that disqualify.
 * Names are tokenised first, so "Pull-Ups", "Pullups" and "pull up" all read as `pullup`.
 *
 * Bilateral movements also refuse single-side variants (archer, one-arm): those are logged as the total
 * of both sides, so 20 archer pull-ups would otherwise pass as 20 pull-ups.
 */
enum class Movement(
    private val required: Set<String>,
    private val excluded: Set<String>,
    private val bilateral: Boolean,
) {
    PULL_UP(
        required = setOf("pullup"),
        excluded = REGRESSIONS + setOf("kipping", "scapular", "scap", "inverted", "australian", "hold", "isometric", "lat"),
        bilateral = true,
    ),
    MUSCLE_UP(
        required = setOf("muscleup"),
        excluded = REGRESSIONS + setOf("transition", "progression", "drill", "low"),
        bilateral = true,
    ),
    PISTOL_SQUAT(
        required = setOf("pistol"),
        excluded = REGRESSIONS + setOf("box", "bench", "chair", "trx", "progression"),
        bilateral = false,
    ),
    L_SIT(
        required = setOf("lsit"),
        // A hanging L-sit is an easier hold; tucked or one-leg are its regressions.
        excluded = REGRESSIONS + setOf("pullup", "chinup", "hanging", "tuck", "tucked", "bent", "single", "one", "raise", "foot"),
        bilateral = true,
    ),
    FRONT_LEVER(
        required = setOf("front", "lever"),
        // Any front-lever hold from tuck up counts; raises, rows and pulls are different movements.
        excluded = REGRESSIONS + setOf("raise", "row", "pull", "pullup"),
        bilateral = true,
    ),
    PUSH_UP(
        required = setOf("pushup"),
        // Not "position": free-exercise-db's close-grip push-up is "Push-Ups - Close Triceps Position".
        // The drills merely done *in* push-up position are caught by stretch / limb / raise instead.
        excluded = REGRESSIONS + setOf(
            "knee", "incline", "wall", "handstand", "pike", "stretch", "limb", "raise", "plank", "renegade",
            "row", "walkover", "smith", "hold",
        ),
        bilateral = true,
    ),
    DIP(
        required = setOf("dip"),
        excluded = REGRESSIONS + setOf("bench", "box", "chair", "seated", "floor", "hip"),
        bilateral = true,
    ),
    ;

    fun matches(name: String): Boolean {
        if (name.isBlank()) return false
        val tokens = tokenize(name)
        if (!tokens.containsAll(required) || tokens.any { it in excluded }) return false
        return !(bilateral && isUnilateral(name))
    }
}

/** Easier versions of any movement — never enough to earn a medal for the real thing. */
private val REGRESSIONS = setOf(
    "assisted", "assist", "band", "banded", "negative", "negatives", "eccentric", "machine",
    "jumping", "jump", "partial", "half",
)

/**
 * Lower-case words, with the two-word movement names joined and plurals dropped, so every spelling of
 * a movement lands on one token: "Pull-Ups" → `pullup`, "L-Sit" → `lsit`, "Dips" → `dip`.
 */
internal fun tokenize(name: String): Set<String> {
    val raw = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    val out = LinkedHashSet<String>()
    var i = 0
    while (i < raw.size) {
        val word = raw[i]
        val next = raw.getOrNull(i + 1)
        val joined = when {
            word in UP_PREFIXES && (next == "up" || next == "ups") -> word + "up"
            word == "l" && (next == "sit" || next == "sits") -> "lsit"
            else -> null
        }
        if (joined != null) {
            out += joined
            i += 2
        } else {
            out += SINGULAR[word] ?: word
            i += 1
        }
    }
    return out
}

private val UP_PREFIXES = setOf("pull", "push", "muscle", "chin", "sit", "step")

private val SINGULAR = mapOf(
    "pullups" to "pullup", "pushups" to "pushup", "muscleups" to "muscleup", "chinups" to "chinup",
    "situps" to "situp", "dips" to "dip", "lsits" to "lsit", "pistols" to "pistol", "levers" to "lever",
    "knees" to "knee", "rows" to "row", "raises" to "raise", "holds" to "hold",
)

/**
 * The six groups "every muscle group" is judged on. free-exercise-db's seventeen muscles fold into
 * them; an exercise counts toward a group through its **primary** muscles only, so a push-up is chest,
 * not also shoulders and arms — otherwise three movements would cover the body.
 */
enum class MuscleGroup { CHEST, BACK, SHOULDERS, ARMS, CORE, LEGS }

internal fun muscleGroupOf(muscle: String): MuscleGroup? = when (muscle.trim().lowercase()) {
    "chest" -> MuscleGroup.CHEST
    "lats", "middle back", "lower back", "traps" -> MuscleGroup.BACK
    "shoulders" -> MuscleGroup.SHOULDERS
    "biceps", "triceps", "forearms" -> MuscleGroup.ARMS
    "abdominals", "abs", "obliques", "core" -> MuscleGroup.CORE
    "quadriceps", "hamstrings", "calves", "glutes", "adductors", "abductors" -> MuscleGroup.LEGS
    else -> null
}

/** The fallback when an exercise isn't in the gallery any more: the region its plan slot was filed under. */
internal fun muscleGroupOf(part: BodyPart): MuscleGroup? = when (part) {
    BodyPart.CHEST -> MuscleGroup.CHEST
    BodyPart.BACK -> MuscleGroup.BACK
    BodyPart.SHOULDERS -> MuscleGroup.SHOULDERS
    BodyPart.ARMS -> MuscleGroup.ARMS
    BodyPart.CORE -> MuscleGroup.CORE
    BodyPart.LEGS, BodyPart.GLUTES -> MuscleGroup.LEGS
    BodyPart.FULL_BODY, BodyPart.CARDIO, BodyPart.OTHER -> null
}
