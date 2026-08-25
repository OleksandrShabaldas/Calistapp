package com.calistapp.app.ui.exercises

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Picture
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.formatKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** The share-card designs. More can be added over time; the picker lists whatever's here. */
private enum class ShareTemplate(val label: String) {
    BOLD("Bold"),
    MUSCLES("Muscles"),
    RECORD("Record"),
}

/**
 * The share sheet: pick a design, see it previewed, and share it as an image. Cards are drawn from
 * text + vectors (never the network demo frame) so the capture is deterministic — the preview is
 * recorded into a [Picture] and rasterised to a PNG on share. The Record card is only offered when
 * there's a personal best to show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    exercise: Exercise,
    bestReps: Int?,
    bestWeightKg: Double?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picture = remember { Picture() }

    val templates = remember(bestReps) {
        buildList {
            add(ShareTemplate.BOLD)
            add(ShareTemplate.MUSCLES)
            if ((bestReps ?: 0) > 0) add(ShareTemplate.RECORD)
        }
    }
    var selected by remember { mutableStateOf(templates.first()) }
    var sharing by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = OnyxRaised) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Share", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Chalk, modifier = Modifier.fillMaxWidth())

            Box(
                Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(18.dp))
                    .drawWithCache {
                        val w = size.width.toInt()
                        val h = size.height.toInt()
                        onDrawWithContent {
                            val pictureCanvas = Canvas(picture.beginRecording(w, h))
                            draw(this, layoutDirection, pictureCanvas, size) {
                                this@onDrawWithContent.drawContent()
                            }
                            picture.endRecording()
                            drawIntoCanvas { it.nativeCanvas.drawPicture(picture) }
                        }
                    },
            ) {
                ShareCardContent(selected, exercise, bestReps, bestWeightKg)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { t ->
                    val on = t == selected
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) Onyx else Ash,
                        modifier = Modifier
                            .clip(Capsule)
                            .background(if (on) Flame else OnyxFillStrong)
                            .clickable { selected = t }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(Capsule)
                    .background(Flame)
                    .clickable(enabled = !sharing) {
                        sharing = true
                        scope.launch {
                            sharePicture(context, picture, exercise.name)
                            sharing = false
                        }
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Share, null, tint = Onyx, modifier = Modifier.size(18.dp))
                    Text(if (sharing) "Preparing…" else "Share image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Onyx)
                }
            }
        }
    }
}

@Composable
private fun ShareCardContent(template: ShareTemplate, exercise: Exercise, bestReps: Int?, bestWeightKg: Double?) {
    when (template) {
        ShareTemplate.BOLD -> BoldCard(exercise)
        ShareTemplate.MUSCLES -> MusclesCard(exercise)
        ShareTemplate.RECORD -> RecordCard(exercise, bestReps ?: 0, bestWeightKg ?: 0.0)
    }
}

@Composable
private fun BoldCard(exercise: Exercise) {
    Column(
        Modifier.fillMaxSize().background(Onyx).padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(exercise.bodyPart.displayName.uppercase(), style = MaterialTheme.typography.labelLarge, color = Flame, fontWeight = FontWeight.Bold)
        Column {
            Box(Modifier.size(width = 44.dp, height = 5.dp).clip(Capsule).background(Flame))
            Spacer(Modifier.height(12.dp))
            Text(exercise.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Chalk)
            Spacer(Modifier.height(6.dp))
            Text(exercise.difficulty.displayName, style = MaterialTheme.typography.titleMedium, color = Ash)
        }
        BrandMark()
    }
}

@Composable
private fun MusclesCard(exercise: Exercise) {
    Column(
        Modifier.fillMaxSize().background(Onyx).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Chalk, textAlign = TextAlign.Center)
        MuscleDiagram(
            primaryMuscles = exercise.primaryMuscles,
            secondaryMuscles = exercise.secondaryMuscles,
            modifier = Modifier.weight(1f),
        )
        Text(
            exercise.primaryMuscles.joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = Flame,
            textAlign = TextAlign.Center,
        )
        BrandMark()
    }
}

@Composable
private fun RecordCard(exercise: Exercise, bestReps: Int, bestWeightKg: Double) {
    Column(
        Modifier.fillMaxSize().background(Onyx).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PERSONAL BEST", style = MaterialTheme.typography.labelLarge, color = Flame, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("$bestReps", style = NumericLarge, color = Chalk)
        Text(if (bestWeightKg > 0) "reps · +${formatKg(bestWeightKg)} kg" else "reps", style = MaterialTheme.typography.titleMedium, color = Ash)
        Spacer(Modifier.height(20.dp))
        Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Chalk, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        BrandMark()
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(Flame))
        Text("Calistapp", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Chalk)
    }
}

private suspend fun sharePicture(context: Context, picture: Picture, exerciseName: String) {
    val uri = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(
            picture.width.coerceAtLeast(1),
            picture.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.parseColor("#0B0B0C"))
            drawPicture(picture)
        }
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "calistapp_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.shareprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "$exerciseName · Calistapp")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
