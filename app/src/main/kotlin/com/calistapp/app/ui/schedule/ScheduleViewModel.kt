package com.calistapp.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.session.SavedWorkoutRepository
import com.calistapp.app.data.session.ScheduleRepository
import com.calistapp.app.data.session.ScheduledItem
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.time.startOfWeekMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    savedWorkoutRepository: SavedWorkoutRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val started = SharingStarted.WhileSubscribed(5_000)

    /** The Monday-start of the current week — everything on this screen is scoped to it. */
    val weekStartMs: Long = startOfWeekMs(System.currentTimeMillis(), zone)
    val weekDates: List<LocalDate> =
        Instant.ofEpochMilli(weekStartMs).atZone(zone).toLocalDate().let { monday ->
            (0..6).map { monday.plusDays(it.toLong()) }
        }

    val savedWorkouts: StateFlow<List<SavedWorkout>> = savedWorkoutRepository.saved
        .stateIn(viewModelScope, started, emptyList())

    val weekPlan: StateFlow<Map<DayOfWeek, List<ScheduledItem>>> = scheduleRepository.weekPlan(weekStartMs)
        .stateIn(viewModelScope, started, emptyMap())

    val recurringByWorkout: StateFlow<Map<String, Set<DayOfWeek>>> = scheduleRepository.recurringDaysByWorkout
        .stateIn(viewModelScope, started, emptyMap())

    fun toggleRecurring(workoutId: String, day: DayOfWeek) {
        val current = recurringByWorkout.value[workoutId].orEmpty()
        val next = if (day in current) current - day else current + day
        viewModelScope.launch { scheduleRepository.setRecurring(workoutId, next) }
    }

    fun skipThisWeek(day: DayOfWeek, workoutId: String) {
        viewModelScope.launch { scheduleRepository.skipThisWeek(weekStartMs, day, workoutId) }
    }

    fun moveThisWeek(from: DayOfWeek, to: DayOfWeek, workoutId: String) {
        viewModelScope.launch { scheduleRepository.moveThisWeek(weekStartMs, from, to, workoutId) }
    }
}
