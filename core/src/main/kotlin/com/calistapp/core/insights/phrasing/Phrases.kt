package com.calistapp.core.insights.phrasing

/**
 * Word choices that slot into templates as `{~group}`. Each entry must read correctly in every
 * template that uses its group, so keep groups narrow (a verb tense, a fixed sentence position).
 */
object Synonyms {
    val ALL: Map<String, List<String>> = mapOf(
        "rose" to listOf("climbed", "went up", "rose", "moved up", "grew", "jumped"),
        "heart" to listOf("your heart", "your heart rate"),
        "recover" to listOf("recovers", "settles", "drops back", "comes down"),
        "faster" to listOf("faster", "quicker"),
        "got" to listOf("got", "only got", "took", "had just"),
        "nothing" to listOf("nothing", "no sets", "not one set", "zero sets"),
        "held" to listOf("held", "stayed", "sat", "stalled"),
        "clean" to listOf("Clean run", "Right on plan", "That's the habit working", "Nothing slipped", "Steady as planned", "Every week held"),
        "felt" to listOf("felt harder", "felt heavier", "took more out of you"),
        "says" to listOf("says", "shows", "suggests"),
        "stronger" to listOf("got stronger", "moved up", "kept climbing", "stepped up", "found new reps", "picked up"),
        "behind" to listOf("got left behind", "fell behind", "got skipped", "went quiet", "missed out", "got forgotten"),
        "easy" to listOf("an easy one", "a light day", "a recovery day", "an easy day"),
        "upfrom" to listOf("up from", "a jump from", "a step up from"),
        "climbing" to listOf("climbing", "moving up", "going up", "improving"),
        "ontrack" to listOf("right on track", "close to perfect", "a strong run"),
    )
}

/** Every slot the insights engine fills. Grouped by the question or element they belong to. */
object Phrases {
    // ---------------------------------------------------------------- 01 stronger
    val STR_GOOD = Slot("str.good", listOf(
        "{ex} {~rose} from {from} to {to} {unit}.",
        "{ex}: {from} to {to} {unit} {period}.",
        "{ex} {~rose} by {delta}, to {to} {unit}.",
        "Your best set of {ex} {~rose} to {to} {unit}.",
        "{ex} {~rose} to {to} {unit}, up from {from}.",
        "New high on {ex}: {to} {unit}, up from {from}.",
        "{period}, {ex} {~rose} from {from} to {to} {unit}.",
        "From {from} to {to} {unit} on {ex} {period}.",
        "{ex} {~rose} {delta} {period}.",
        "{ex} now tops out at {to} {unit}, {delta} more than before.",
        "{to} {unit} on {ex} now, {~upfrom} {from}.",
        "{ex} {~rose} again, now {to} {unit}.",
        "Your {ex} best {~rose} to {to} {unit}.",
        "{ex} {~rose}, {from} to {to} {unit}.",
        "{delta} added to your {ex} best, now {to} {unit}.",
        "{ex} keeps {~climbing}, {to} {unit} at best.",
        "Best {ex} set {period}: {to} {unit}, from {from}.",
        "{ex} {~rose} past {from}, all the way to {to} {unit}.",
        "The {ex} number {~rose} from {from} to {to}.",
        "{period}, your top {ex} set {~rose} to {to} {unit}.",
    ))
    val STR_MIXED = Slot("str.mixed", listOf(
        "{ex} {~rose} to {to} {unit}, but {stuck} {~held} at {stuckAt} for {stuckRuns} sessions.",
        "{ex} {~rose} to {to} {unit}. {stuck} {~held} at {stuckAt}.",
        "Progress on {ex}, a plateau on {stuck}.",
        "{ex} {~rose} from {from} to {to} {unit}, while {stuck} {~held} at {stuckAt}.",
        "One up, one stuck: {ex} reached {to} {unit}, {stuck} {~held} at {stuckAt}.",
        "{stuck} {~held} at {stuckAt} for {stuckRuns} sessions. {ex} kept moving, to {to} {unit}.",
        "{ex} is moving. {stuck} hasn't budged from {stuckAt} in {stuckRuns} sessions.",
        "{ex} {~rose} to {to} {unit}, but {slip} dipped from {slipFrom} to {slipTo}.",
        "{slip} slipped to {slipTo} while {ex} {~rose} to {to} {unit}.",
    ))
    val STR_WATCH = Slot("str.watch", listOf(
        "No new bests {period}. {stuck} has {~held} at {stuckAt} for {stuckRuns} sessions.",
        "{stuck} {~held} at {stuckAt} for {stuckRuns} sessions running.",
        "Nothing moved up {period}. {stuck} is parked at {stuckAt}.",
        "Your main moves {~held} where they were {period}.",
        "{slip} slipped from {slipFrom} to {slipTo} {period}.",
        "A flat stretch. No movement beat its best {period}.",
        "{stuck} has been at {stuckAt} for {stuckRuns} sessions now.",
        "No records {period}. {stuck} is the one to unstick.",
    ))
    val STR_NONE = Slot("str.none", listOf(
        "Log your main moves a few more times and this fills in.",
        "Two more sessions with the same moves and there's a trend to read.",
        "Not enough repeats yet to call it. Keep logging your main moves.",
        "This needs the same movements across a few sessions.",
        "Come back after a couple more workouts.",
        "Too early to tell. A few more sessions will show it.",
    ))

    // ---------------------------------------------------------------- 02 fitter
    val FIT_GOOD = Slot("fit.good", listOf(
        "{~heart} {~recover} {delta} bpm {~faster} than {when}.",
        "Recovery is {delta} bpm better than {when}.",
        "After a hard set you drop {now} bpm in a minute, {delta} more than {when}.",
        "Your recovery {~rose} by {delta} bpm since {since}.",
        "{delta} bpm more recovery per minute than {when}.",
        "{~heart} {~recover} {~faster} than {when}, {now} bpm in the first minute.",
        "Between sets {~heart} {~recover} {delta} bpm {~faster} than {when}.",
        "Recovery is up to {now} bpm a minute, from {prev}.",
        "You're bouncing back {delta} bpm {~faster} than {when}.",
        "{now} bpm of recovery in a minute now, up from {prev}.",
    ))
    val FIT_GOOD_HR = Slot("fit.goodhr", listOf(
        "{name} now runs at {last} bpm, down from {first}.",
        "The same {name} workout costs your heart {diff} bpm less than it did.",
        "{name}: {first} to {last} bpm over {runs} runs.",
        "Your heart works {diff} bpm less on {name} than it used to.",
        "{name} got easier on your heart, {first} down to {last} bpm.",
    ))
    val FIT_MIXED = Slot("fit.mixed", listOf(
        "Recovery is holding at about {now} bpm a minute.",
        "{~heart} {~recover} about as fast as {when}.",
        "No change in recovery, around {now} bpm a minute.",
        "Steady recovery, near {now} bpm per minute.",
        "Recovery hasn't moved much since {since}.",
        "About the same recovery as {when}, {now} bpm a minute.",
    ))
    val FIT_WATCH = Slot("fit.watch", listOf(
        "Recovery is {delta} bpm slower than {when}.",
        "{~heart} {~recover} {delta} bpm slower than {when}.",
        "Recovery dipped to {now} bpm a minute, from {prev}.",
        "You're recovering a little slower than {when}.",
        "Recovery fell by {delta} bpm since {since}.",
        "{delta} bpm less recovery per minute than {when}.",
    ))
    val FIT_NONE = Slot("fit.none", listOf(
        "Recovery shows up after a few sessions with rests and a heart rate.",
        "Wear your watch or band through a few sessions and this fills in.",
        "Needs heart rate from a few more workouts.",
        "Not enough heart-rate data yet to compare.",
        "A few more sessions with heart rate and this one appears.",
    ))

    // ---------------------------------------------------------------- 03 balanced
    val BAL_GOOD = Slot("bal.good", listOf(
        "Push, pull, legs and core all got their share.",
        "A fair split across push, pull, legs and core.",
        "Nothing was left out {period}.",
        "Every part got trained {period}.",
        "No group fell under {minPct}% of your sets.",
        "Your sets spread evenly across the body.",
        "Push and pull stayed even, and legs kept up.",
        "Legs got {legsPct}% and the rest was shared evenly.",
    ))
    val BAL_MIXED = Slot("bal.mixed", listOf(
        "{low} {~got} {lowPct}% of your sets. {muscle}: {~nothing} in {days} days.",
        "{low} {~got} {lowPct}% of your sets.",
        "Only {lowPct}% of your sets went to {lowNoun}.",
        "{lowPct}% for {lowNoun}. Last set for {muscleLower}: {days} days ago.",
        "The gap is {lowNoun}, at {lowPct}% of your sets.",
        "{muscle}: {~nothing} in {days} days. {low} {~got} {lowPct}% overall.",
        "Your split leans away from {lowNoun}, at {lowPct}% of sets.",
        "Most sets went elsewhere. {low} {~got} {lowPct}%.",
        "{days} days since your last set for {muscleLower}.",
        "{low} {~got} {lowPct}% of the work {period}.",
    ))
    val BAL_WATCH = Slot("bal.watch", listOf(
        "{low} got just {lowPct}% of your sets. {muscle}: {~nothing} in {days} days.",
        "{lowNoun} barely showed up {period}, {lowPct}% of your sets.",
        "{lowPct}% for {lowNoun}. That's a real gap.",
        "{muscle}: {~nothing} in {days} days, and {lowNoun} got {lowPct}%.",
        "Almost nothing for {lowNoun}, {lowPct}% of your sets.",
        "{days} days without a set for {muscleLower}.",
        "{low} {~got} {lowPct}% of your sets. The rest went to {topNoun}.",
    ))
    val BAL_NONE = Slot("bal.none", listOf(
        "A few more sessions and the split becomes clear.",
        "Needs about 20 sets before it means anything.",
        "Not enough sets yet to judge the balance.",
        "Train a few more times and this shows where your sets go.",
    ))

    // ---------------------------------------------------------------- 04 consistent
    val CON_GOOD = Slot("con.good", listOf(
        "{done} of {planned} planned days. {~clean}.",
        "You trained {done} of the {planned} days you planned.",
        "{done} of {planned} days done, as planned.",
        "Every planned day but {missedWord}.",
        "{done} out of {planned} planned days.",
        "On plan, {done} of {planned} days.",
        "The plan held, {done} of {planned} days.",
        "{pct}% of planned days done.",
        "{done} of {planned} planned days happened. {~clean}.",
        "Planned {planned}, trained {done}. {~clean}.",
        "{done} planned days out of {planned}, {~ontrack}.",
        "You kept {done} of {planned} planned days.",
        "{pct}% of the plan, {done} of {planned} days.",
    ))
    val CON_GOOD_NOPLAN = Slot("con.goodnp", listOf(
        "{perWeek} sessions a week, every week.",
        "A session every week for {weeksWord} weeks.",
        "No empty weeks, {perWeek} sessions a week on average.",
        "{weeksWord} weeks, all of them trained.",
        "You trained every week, about {perWeek} times a week.",
        "Not one empty week, {perWeek} sessions a week.",
    ))
    val CON_MIXED = Slot("con.mixed", listOf(
        "{done} of {planned} planned days. {missLine}.",
        "{done} of {planned} planned days, so mostly on plan.",
        "Mostly on plan, {done} of {planned} days.",
        "{missedWord} planned days slipped. {missLine}.",
        "{done} of {planned}. {missLine}.",
        "You hit {done} of {planned} planned days.",
    ))
    val CON_WATCH = Slot("con.watch", listOf(
        "{done} of {planned} planned days. {missLine}.",
        "Only {done} of {planned} planned days happened.",
        "The plan slipped, {done} of {planned} days.",
        "Planned days kept getting skipped, {done} of {planned} done.",
        "{missedWord} planned days missed {period}.",
    ))
    val CON_MIXED_NOPLAN = Slot("con.mixednp", listOf(
        "{trainedWeeks} of {weeks} weeks had a session.",
        "A session most weeks, {trainedWeeks} of {weeks}.",
        "Some weeks went empty, {trainedWeeks} of {weeks} trained.",
    ))
    val CON_WATCH_NOPLAN = Slot("con.watchnp", listOf(
        "Only {trainedWeeks} of {weeks} weeks had a session.",
        "Long gaps {period}, {trainedWeeks} of {weeks} weeks trained.",
        "Most weeks went empty, {trainedWeeks} of {weeks} trained.",
    ))
    val CON_NONE = Slot("con.none", listOf(
        "Give it a week of training and this fills in.",
        "Needs a week or two of history.",
        "Too soon to talk about habits. Keep going.",
    ))
    val MISS_BOTH = Slot("miss.both", listOf(
        "Both misses were {day}s",
        "The two misses were both {day}s",
        "Each miss fell on a {day}",
        "{day} slipped twice",
    ))
    val MISS_SOME = Slot("miss.some", listOf(
        "{n} of the {m} misses were {day}s",
        "{day} is the day that slips, {n} of {m} misses",
        "Most misses landed on {day}s",
        "{n} misses fell on a {day}",
    ))

    // ---------------------------------------------------------------- 05 recovering
    val REC_GOOD = Slot("rec.good", listOf(
        "Your effort matches what your heart says.",
        "How hard it felt lines up with your heart rate.",
        "Felt effort and heart rate agree.",
        "No sign of fatigue in your last sessions.",
        "Your last sessions felt about as hard as they were.",
        "Effort and heart rate are in step.",
    ))
    val REC_MIXED = Slot("rec.mixed", listOf(
        "One recent session felt harder than your heart rate says.",
        "A session lately took more out of you than your heart showed.",
        "Mostly fine, with one session that felt heavier than it was.",
        "One workout felt off. Worth watching.",
    ))
    val REC_WATCH = Slot("rec.watch", listOf(
        "Your last {count} sessions {~felt} than your heart rate {~says}.",
        "{count} sessions running {~felt} than they should have.",
        "Effort is running ahead of your heart rate lately.",
        "Your last {count} workouts {~felt} than your heart rate {~says}.",
        "Fatigue is showing. {count} sessions {~felt} than the numbers.",
    ))
    val REC_LOAD = Slot("rec.load", listOf(
        "This week's load is {ratio}× your usual.",
        "A big week, {ratio}× your recent average.",
        "Load jumped to {ratio}× a normal week.",
    ))
    val REC_NONE = Slot("rec.none", listOf(
        "Rate how hard sessions feel and this fills in.",
        "Needs a few sessions with an effort rating.",
        "Add an effort rating after workouts to see this.",
        "Not enough rated sessions yet.",
    ))

    // ---------------------------------------------------------------- cover headline clauses
    val HL_GOOD_STRONGER = Slot("hl.g.str", listOf("{group} {~stronger}.", "New bests on {ex}.", "{ex} hit a new high."))
    val HL_GOOD_FITTER = Slot("hl.g.fit", listOf(
        "Your heart got fitter.", "Recovery sped up.", "Your engine improved.",
        "Your heart bounces back faster.", "Fitness is climbing.", "Recovery got quicker.",
    ))
    val HL_GOOD_BALANCED = Slot("hl.g.bal", listOf("Every part got trained.", "A balanced {periodNoun}.", "The split stayed even.", "Nothing got left out."))
    val HL_GOOD_CONSISTENT = Slot("hl.g.con", listOf(
        "The habit held.", "Every planned day happened.", "You showed up.", "The plan held.", "Consistency paid off.", "No weeks went missing.",
    ))
    val HL_GOOD_RECOVERING = Slot("hl.g.rec", listOf("You're recovering well.", "Recovery is on track.", "No fatigue to speak of."))
    val HL_BAD_STRONGER = Slot("hl.b.str", listOf("{stuck} stalled.", "Strength went flat.", "No new bests this {periodNoun}.", "The numbers held still."))
    val HL_BAD_FITTER = Slot("hl.b.fit", listOf("Recovery slowed.", "Your heart needs easier days.", "Fitness dipped a little."))
    val HL_BAD_BALANCED = Slot("hl.b.bal", listOf("{group} {~behind}."))
    val HL_BAD_CONSISTENT = Slot("hl.b.con", listOf("{day}s keep slipping.", "The plan slipped.", "A few days went missing.", "Some weeks went quiet."))
    val HL_BAD_RECOVERING = Slot("hl.b.rec", listOf("Fatigue is creeping in.", "You need an easy day.", "Your body is asking for rest.", "Effort is outrunning your heart."))
    val HL_ALL_GOOD = Slot("hl.allgood", listOf("Everything moved the right way.", "A clean sweep this {periodNoun}.", "All five went your way."))
    val HL_ALL_BAD = Slot("hl.allbad", listOf("A tough {periodNoun}.", "Not your best {periodNoun}, and that's fine.", "A {periodNoun} to reset."))
    val HL_EARLY = Slot("hl.early", listOf("Early days. Here's what shows so far.", "The first picture is forming.", "A start. More sessions, more to see."))
    val HL_EMPTY = Slot("hl.empty", listOf(
        "Your first issue starts with your first workout.",
        "Train once and this page comes alive.",
        "Nothing to report yet. Your first session changes that.",
    ))

    // ---------------------------------------------------------------- cover summary line
    val SUM_VERDICTS = Slot("sum.v", listOf(
        "{good} going well, {work} to work on.",
        "{good} up, {work} to fix.",
        "{good} wins and {work} to watch.",
        "{good} good, {work} needing work.",
    ))
    val SUM_ALL_GOOD = Slot("sum.allgood", listOf(
        "{good} going well, nothing to fix.",
        "{good} up, nothing to work on.",
        "All good so far, {good} of them.",
    ))
    val SUM_ALL_WORK = Slot("sum.allwork", listOf(
        "{work} to work on.",
        "{work} to watch, none going well yet.",
        "{work} needing work this time.",
    ))
    val SUM_UP = Slot("sum.up", listOf(
        "{sessions}, {diff} more than {prev}.",
        "{sessions}, up {diff} on {prev}.",
        "{sessions} in all, {diff} more than {prev}.",
    ))
    val SUM_DOWN = Slot("sum.down", listOf("{sessions}, {diff} fewer than {prev}.", "{sessions}, down {diff} on {prev}."))
    val SUM_SAME = Slot("sum.same", listOf("{sessions}, same as {prev}.", "{sessions}, level with {prev}."))
    val SUM_FIRST = Slot("sum.first", listOf("{sessions} so far.", "{sessions} in your first {periodNoun}."))

    // ---------------------------------------------------------------- moves (card footers, recap)
    val MOVE_ADD_LOAD = Slot("mv.load.t", listOf("{ex} is ready for weight", "Add weight to {ex}", "Time to load {ex}", "{ex} with +5 kg"))
    val MOVE_ADD_LOAD_D = Slot("mv.load.d", listOf("{to} {unit} now · add one +5 kg set", "You're at {to} {unit} · try a +5 kg set", "{to} {unit} is enough to add load"))
    val MOVE_UNSTICK = Slot("mv.unstick.t", listOf("Change up {stuck}", "Shake up {stuck}", "Try a new angle on {stuck}"))
    val MOVE_UNSTICK_D = Slot("mv.unstick.d", listOf(
        "{stuckRuns} sessions at {stuckAt} · try slower reps",
        "Stuck at {stuckAt} · a harder variation helps",
        "{stuckAt} for {stuckRuns} sessions · change the tempo",
    ))
    val MOVE_GROUP_DAY = Slot("mv.day.t", listOf("Add a {dayNoun} day", "Put a {dayNoun} day in this week", "One {dayNoun} day this week"))
    val MOVE_KEEP_CIRCUITS = Slot("mv.circ.t", listOf("Keep the circuit days", "Hold on to the circuits"))
    val MOVE_KEEP_CIRCUITS_D = Slot("mv.circ.d", listOf("Recovery climbed once you added them", "They're what moved your recovery"))
    val MOVE_KEEP_RHYTHM = Slot("mv.rhythm.t", listOf("Keep this rhythm", "Keep doing what you're doing", "Same again next {periodNoun}"))
    val MOVE_KEEP_RHYTHM_D = Slot("mv.rhythm.d", listOf("{perWeek} sessions a week is working", "It's working · keep it steady"))
    val MOVE_EASY_CARDIO = Slot("mv.cardio.t", listOf("Add one easy circuit a week", "Add a light circuit day"))
    val MOVE_EASY_CARDIO_D = Slot("mv.cardio.d", listOf("Short and steady brings recovery back", "Easy rounds rebuild recovery"))
    val MOVE_SHORT_DAY = Slot("mv.short.t", listOf("Make {day} the short one", "Shorten {day}s", "Give {day} a 20-minute version"))
    val MOVE_SHORT_DAY_D = Slot("mv.short.d", listOf("A 20-minute version keeps the habit", "Short beats skipped"))
    val MOVE_PLAN_WEEK = Slot("mv.plan.t", listOf("Plan your week", "Pick your training days"))
    val MOVE_PLAN_WEEK_D = Slot("mv.plan.d", listOf("Choose the days and the app keeps count", "Planned days are easier to keep"))
    val MOVE_EASY_DAY = Slot("mv.easy.t", listOf("Make tomorrow {~easy}"))
    val MOVE_EASY_DAY_D = Slot("mv.easy.d", listOf(
        "You slept {sleep} before those {count}",
        "Your last {count} felt harder than your heart rate says",
        "Your body is asking for a lighter day",
    ))
    val MOVE_AS_PLANNED = Slot("mv.asplan.t", listOf("Train as planned", "Carry on as planned"))
    val MOVE_AS_PLANNED_D = Slot("mv.asplan.d", listOf("Effort and heart rate agree", "Nothing to change this week"))

    // ---------------------------------------------------------------- records
    val RECORD_NOTE = Slot("rec.note", listOf(
        "Up from {prev}, and the first time past {prev} since you started.",
        "Past {prev} for the first time.",
        "Your first time beyond {prev}.",
        "Beat {prev}, your old best.",
        "{prev} was the best until {date}.",
    ))
}
