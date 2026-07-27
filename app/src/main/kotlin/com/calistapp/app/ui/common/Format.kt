package com.calistapp.app.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** "1:05:09" or "05:09" clock format for a duration in ms. */
fun formatClock(ms: Long): String {
    val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** "12m 30s" compact duration. */
fun formatCompact(ms: Long): String {
    val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

private val dateFmt = SimpleDateFormat("EEE d MMM • HH:mm", Locale.getDefault())

fun formatDate(ms: Long): String = dateFmt.format(Date(ms))
