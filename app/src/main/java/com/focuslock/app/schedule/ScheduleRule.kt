package com.focuslock.app.schedule

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime

@Serializable
data class ScheduleRule(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val name: String = "Schedule",
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val enabled: Boolean = true,
    val blockAllApps: Boolean = false,
    val blockedApps: Set<String> = emptySet(),
    val enableFeedRules: Set<String> = emptySet(),
) {
    fun isActiveNow(): Boolean {
        if (!enabled) return false
        val now = java.time.LocalDateTime.now()
        val dow = now.dayOfWeek.value // 1=Mon 7=Sun
        val time = now.toLocalTime()
        val start = LocalTime.of(startHour, startMinute)
        val end = LocalTime.of(endHour, endMinute)

        return if (start <= end) {
            dow in daysOfWeek && time in start..end
        } else {
            // Overnight: e.g. 22:00 - 07:00
            if (time >= start) dow in daysOfWeek
            else {
                val prevDow = if (dow == 1) 7 else dow - 1
                prevDow in daysOfWeek && time <= end
            }
        }
    }

    val daysLabel: String get() {
        if (daysOfWeek.size == 7) return "Every day"
        if (daysOfWeek == setOf(1, 2, 3, 4, 5)) return "Weekdays"
        if (daysOfWeek == setOf(6, 7)) return "Weekends"
        return daysOfWeek.sorted().joinToString(", ") {
            DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }
        }
    }

    val timeLabel: String get() = "%d:%02d – %d:%02d".format(startHour, startMinute, endHour, endMinute)
}
