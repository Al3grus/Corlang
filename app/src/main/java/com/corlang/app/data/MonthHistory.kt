package com.corlang.app.data

import java.time.LocalDate

/**
 * The month grid on Progress: which cells are drawn, in what state, and what the count line says.
 *
 * Pure, because the rules here are the kind that are easy to get subtly wrong and impossible to
 * check by looking at a screenshot: what a leading blank is, whether today counts, and what the
 * denominator of "3 of 5 days" actually means.
 *
 * The rule that everything else follows from: **today is pending, never missed.** A day is only
 * judged once it is over. Today joins neither side of the count until it is banked, which is the
 * same statement the grey streak flame makes on Learn, and the two must never disagree.
 */
object MonthHistory {

    enum class DayState { Blank, Banked, Missed, Today, Future }

    data class DayCell(val day: Int?, val state: DayState, val date: LocalDate?)

    data class Month(
        val cells: List<DayCell>,
        /** Days in this month that are over: banked plus missed. Today is not one of them. */
        val settled: Int,
        val banked: Int,
    )

    /**
     * [monthOffset] 0 is the month containing [today], negative steps back.
     * [bankedDates] is every date with a completed lesson, in any month.
     */
    fun build(today: LocalDate, monthOffset: Int, bankedDates: Set<LocalDate>): Month {
        val first = today.withDayOfMonth(1).plusMonths(monthOffset.toLong())
        // Monday-first: ISO puts Monday at 1, so Sunday (7) becomes the last column.
        val lead = (first.dayOfWeek.value + 6) % 7
        val cells = List(lead) { DayCell(null, DayState.Blank, null) } +
            (1..first.lengthOfMonth()).map { d ->
                val date = first.withDayOfMonth(d)
                DayCell(
                    d,
                    when {
                        date.isEqual(today) -> DayState.Today
                        date.isAfter(today) -> DayState.Future
                        date in bankedDates -> DayState.Banked
                        else -> DayState.Missed
                    },
                    date
                )
            }
        return Month(
            cells = cells,
            settled = cells.count { it.state == DayState.Banked || it.state == DayState.Missed },
            banked = cells.count { it.state == DayState.Banked },
        )
    }

    /**
     * How far back the arrows may go: the month of the learner's first banked day. Stepping back
     * into months that predate the course would be a walk through empty grids.
     */
    fun earliestOffset(today: LocalDate, bankedDates: Set<LocalDate>): Int {
        val first = bankedDates.minOrNull() ?: return 0
        val months = (first.year - today.year) * 12 + (first.monthValue - today.monthValue)
        return minOf(0, months)
    }
}
