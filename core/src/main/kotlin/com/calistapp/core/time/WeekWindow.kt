package com.calistapp.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * First millisecond of the calendar week containing [nowMs].
 *
 * A calendar week, not the last seven days: "this week" on a dashboard is a thing people compare
 * against last week and plan the rest of against, and a window that silently slides forward every
 * time you open the app is neither. Monday-first by default, which is what training weeks are
 * everywhere the ISO calendar is used.
 */
fun startOfWeekMs(
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    firstDay: DayOfWeek = DayOfWeek.MONDAY,
): Long = Instant.ofEpochMilli(nowMs)
    .atZone(zone)
    .with(TemporalAdjusters.previousOrSame(firstDay))
    .toLocalDate()
    .atStartOfDay(zone)
    .toInstant()
    .toEpochMilli()
