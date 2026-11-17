package com.calistapp.app.ui.medals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.insights.MedalsRepository
import com.calistapp.core.medals.MedalAward
import com.calistapp.core.medals.MedalCatalog
import com.calistapp.core.medals.MedalDefinition
import com.calistapp.core.medals.MedalKind
import com.calistapp.core.medals.MedalLine
import com.calistapp.core.medals.MedalReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** The medal case, the achievements section and the unlock moment all read the same report. */
@HiltViewModel
class MedalsViewModel @Inject constructor(
    private val medals: MedalsRepository,
) : ViewModel() {
    val report: StateFlow<MedalReport?> = medals.report
    val seen: StateFlow<Set<String>> = medals.seen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val unseen: StateFlow<List<MedalAward>> = medals.unseen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markSeen(keys: Collection<String>) {
        viewModelScope.launch { medals.markSeen(keys) }
    }
}

/** One struck tier with the line it belongs to. */
data class StruckMedal(val line: MedalLine, val award: MedalAward) {
    val definition: MedalDefinition get() = line.definition
}

/** A line still being worked toward, with how far along it is. */
data class NextMedal(val line: MedalLine, val tierIndex: Int, val fraction: Float, val label: String, val toGo: String) {
    val definition: MedalDefinition get() = line.definition
}

/** Every struck tier, newest first. */
fun MedalReport.struckNewestFirst(): List<StruckMedal> {
    val byId = lines.associateBy { it.definition.id }
    return awards.asReversed().mapNotNull { a -> byId[a.medalId]?.let { StruckMedal(it, a) } }
}

/**
 * The lines closest to their next tier, nearest first: measurable, under way (more than zero), and not
 * hidden. Event lines (a comeback, a cold day) have no bar to fill, so they never show here.
 */
fun MedalReport.closestNext(count: Int = 3): List<NextMedal> = lines.mapNotNull { line ->
    val p = line.progress ?: return@mapNotNull null
    val next = p.nextTierIndex ?: return@mapNotNull null
    if (!p.measurable || p.fraction <= 0.0 || line.definition.hidden) return@mapNotNull null
    NextMedal(line, next, p.fraction.toFloat().coerceIn(0f, 1f), p.label, p.toGo)
}.sortedByDescending { it.fraction }.take(count)

val MedalReport.struckCount: Int get() = awards.size
val MedalReport.totalTiers: Int get() = lines.sumOf { it.definition.tiers.size }

fun MedalReport.countsFor(kind: MedalKind): Pair<Int, Int> {
    val ls = lines.filter { it.definition.kind == kind }
    return ls.sumOf { it.earnedTiers } to ls.sumOf { it.definition.tiers.size }
}

/** "Pull-ups 15" for a ladder's rung, the plain name for a one-off. Hidden and unearned: "Hidden". */
fun MedalDefinition.titleFor(tierIndex: Int, earned: Boolean = true): String = when {
    hidden && !earned -> MedalCatalog.HIDDEN_NAME
    tiers.size > 1 -> "$name ${tiers[tierIndex.coerceIn(0, tiers.lastIndex)].label}"
    else -> name
}

/** "11 reps in one set": the engraving, said in a sentence. */
fun MedalAward.valueLine(): String =
    engraving.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

/** "11 REPS IN ONE SET · NOV 6 2026": what is struck into the rim. */
fun MedalAward.rimText(zone: ZoneId = ZoneId.systemDefault()): String =
    "$engraving · ${RimDate.format(Instant.ofEpochMilli(earnedAtMs).atZone(zone)).uppercase(Locale.ROOT)}"

fun MedalAward.shortDate(zone: ZoneId = ZoneId.systemDefault()): String =
    ShortDate.format(Instant.ofEpochMilli(earnedAtMs).atZone(zone))

fun MedalAward.longDate(zone: ZoneId = ZoneId.systemDefault()): String =
    RimDate.format(Instant.ofEpochMilli(earnedAtMs).atZone(zone))

private val RimDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH)
private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** "Skills · Copper", the eyebrow over a medal's name. */
fun StruckMedal.kindAndMetal(): String =
    "${definition.kind.label} · ${MedalPalette.metalName(award.metal)}"

/**
 * The one sample shown before anything is under way: the first pull-up, the medal most people earn
 * first, with a placeholder where their own number and date will be struck.
 */
val PreviewMedal: MedalDefinition get() = MedalCatalog.byId("first-pull-up") ?: MedalCatalog.all.first()

/** The medals most people start with, shown on an empty case. */
val StarterMedals: List<Pair<String, String>> = listOf(
    "first-pull-up" to "Yours the day you do one",
    "week-streak" to "Train once a week, 4 weeks running",
    "sessions" to "50 workouts logged",
)
