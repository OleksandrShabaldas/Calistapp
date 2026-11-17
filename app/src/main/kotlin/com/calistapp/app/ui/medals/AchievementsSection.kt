package com.calistapp.app.ui.medals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.common.pressScale
import com.calistapp.app.ui.common.rememberInteraction
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Body
import com.calistapp.app.ui.theme.CardFlat
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.core.medals.MedalReport
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val NEW_FOR_MS = 14L * 24 * 60 * 60 * 1000

/**
 * The achievements block on Insights. With medals: the newest one big, rim and all, with Share, then
 * the case (six more) — or, while the case is still nearly empty, the medals closest next. With none:
 * "C · Preview the reward" (a faded sample on day 0; the closest medal as a ghost once under way).
 */
@Composable
fun AchievementsSection(
    report: MedalReport?,
    onAll: () -> Unit,
    onOpen: (StruckMedal) -> Unit,
) {
    if (report == null) return
    val struck = report.struckNewestFirst()
    val closest = report.closestNext(3)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ACHIEVEMENTS · ${report.struckCount} OF ${report.totalTiers}",
                style = MedalMono.copy(letterSpacing = 1.6.sp),
                color = Ash,
                modifier = Modifier.weight(1f),
            )
            Text(
                "All medals",
                style = TextStyle(fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = FlameGlow,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAll).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        if (struck.isEmpty()) {
            PreviewCard(closest.firstOrNull(), onAll)
        } else {
            NewestCard(struck.first(), first = struck.size == 1, onOpen = onOpen)
            val others = struck.drop(1)
            if (others.size >= 3) {
                others.take(6).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m -> CaseSlot(m, Modifier.weight(1f)) { onOpen(m) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else if (closest.isNotEmpty()) {
                Column(Modifier.padding(top = 12.dp)) {
                    closest.forEachIndexed { i, n -> NextRow(n, top = i > 0) }
                }
            }
        }
    }
}

@Composable
private fun NewestCard(m: StruckMedal, first: Boolean, onOpen: (StruckMedal) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val isNew = System.currentTimeMillis() - m.award.earnedAtMs < NEW_FOR_MS
    val interaction = rememberInteraction()
    MedalCardShell(glow = true) {
        Pill(if (isNew) "NEW · ${m.award.shortDate().uppercase()}" else m.award.shortDate().uppercase(), hot = isNew)
        Box(
            Modifier
                .padding(top = 10.dp)
                .width(120.dp)
                .pressScale(interaction)
                .clickable(interaction, indication = null) { onOpen(m) },
        ) {
            Medal(m.definition, m.award.tierIndex, MedalLook.STRUCK, rim = m.award.rimText())
        }
        CardTitle(m.definition.titleFor(m.award.tierIndex))
        CardBody(if (first) "Your first medal. ${m.award.valueLine()}, struck into the rim." else "${m.award.valueLine()}, struck into the rim with the date.")
        OutlinePill(if (sharing) "Preparing…" else "Share medal", icon = true) {
            if (sharing) return@OutlinePill
            sharing = true
            scope.launch {
                shareMedal(context, m, TextMeasurer(resolver, density, LayoutDirection.Ltr), density)
                sharing = false
            }
        }
    }
}

@Composable
private fun PreviewCard(closest: NextMedal?, onAll: () -> Unit) {
    MedalCardShell(glow = closest != null) {
        if (closest == null) {
            Pill("PREVIEW", hot = false)
            Medal(PreviewMedal, 0, MedalLook.GHOST, modifier = Modifier.padding(top = 10.dp).width(120.dp), ghost = 0.3f, rim = "YOUR NUMBER · YOUR DATE")
            CardTitle("Your first medal")
            CardBody("Struck with your own number and date the day you earn it.")
        } else {
            Pill("${(closest.fraction * 100).roundToInt()} % THERE", hot = true)
            Medal(closest.definition, closest.tierIndex, MedalLook.GHOST, modifier = Modifier.padding(top = 10.dp).width(120.dp), ghost = closest.fraction)
            CardTitle(closest.definition.titleFor(closest.tierIndex))
            CardBody("${closest.toGo.replaceFirstChar { it.uppercase() }}. It fills in as you get closer.")
        }
        OutlinePill("See all ${MedalCountLabel}", icon = false, onClick = onAll)
    }
}

/** "40": how many medals there are, said on the empty card's button. */
private val MedalCountLabel: Int get() = com.calistapp.core.medals.MedalCatalog.all.sumOf { it.tiers.size }

@Composable
private fun CaseSlot(m: StruckMedal, modifier: Modifier, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    Column(
        modifier.pressScale(interaction).clickable(interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Medal(m.definition, m.award.tierIndex, MedalLook.STRUCK, modifier = Modifier.width(84.dp))
        Text(
            m.definition.titleFor(m.award.tierIndex),
            style = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, hyphens = Hyphens.Auto),
            color = Chalk,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(m.award.shortDate().uppercase(), style = MedalMono.copy(fontSize = 9.5.sp, letterSpacing = 0.6.sp), color = Ash, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun MedalCardShell(glow: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(CardFlat)
            .then(
                if (glow) Modifier.background(Brush.radialGradient(0f to FlameGlow.copy(alpha = 0.16f), 0.7f to Color.Transparent)) else Modifier,
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Pill(text: String, hot: Boolean) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        text,
        style = MedalMono.copy(fontSize = 9.5.sp, letterSpacing = 1.2.sp),
        color = if (hot) FlameGlow else Ash,
        modifier = Modifier
            .clip(shape)
            .background(if (hot) FlameHot.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (hot) FlameHot.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp, hyphens = Hyphens.Auto),
        color = Chalk,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun CardBody(text: String) {
    Text(
        text,
        style = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 19.sp),
        color = Ash,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun OutlinePill(text: String, icon: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .padding(top = 14.dp)
            .height(40.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon) Icon(Icons.Filled.IosShare, null, tint = Chalk, modifier = Modifier.size(15.dp))
        Text(text, style = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp), color = Chalk)
    }
}
