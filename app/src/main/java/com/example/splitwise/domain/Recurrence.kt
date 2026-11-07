package com.example.splitwise.domain

import java.time.LocalDate

enum class Interval(val label: String) { NONE("Never"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly") }

object Recurrence {
    fun next(epochDay: Long, interval: Interval): Long {
        val d = LocalDate.ofEpochDay(epochDay)
        val n = when (interval) {
            Interval.NONE -> return epochDay
            Interval.DAILY -> d.plusDays(1)
            Interval.WEEKLY -> d.plusWeeks(1)
            Interval.MONTHLY -> d.plusMonths(1)
            Interval.YEARLY -> d.plusYears(1)
        }
        return n.toEpochDay()
    }
}
