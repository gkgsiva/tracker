package com.gkgsiva.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class BreakLabelTest {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    @Test
    fun guessesFromTheStartTime() {
        assertEquals(BreakLabel.BREAKFAST, BreakLabel.guess(t(8, 29)))
        assertEquals(BreakLabel.TEA, BreakLabel.guess(t(10, 45)))
        assertEquals(BreakLabel.LUNCH, BreakLabel.guess(t(12, 30)))
        assertEquals(BreakLabel.TEA, BreakLabel.guess(t(16, 0)))
    }

    @Test
    fun unknownOrMissingLabelShowsAsBreak() {
        assertEquals("Break", BreakLabel.textOf(null))
        assertEquals("Break", BreakLabel.textOf("NAP"))
        assertEquals("Lunch", BreakLabel.textOf("LUNCH"))
    }

    @Test
    fun eachBreakIsOneRowAndARunningBreakHasNoEnd() {
        val punches = listOf(
            Punch(t(8, 18), isIn = true),
            Punch(t(8, 29), isIn = false, label = "BREAKFAST"),
            Punch(t(8, 51), isIn = true),
            Punch(t(12, 30), isIn = false, label = "LUNCH"),
            Punch(t(13, 10), isIn = true),
            Punch(t(14, 25), isIn = false),
        )
        val rows = EntryRow.of(punches.shuffled()) { it }
        assertEquals(
            listOf(
                EntryRow.Login(punches[0]),
                EntryRow.Break(punches[1], punches[2]),
                EntryRow.Break(punches[3], punches[4]),
                EntryRow.Break(punches[5], null),
            ),
            rows,
        )
    }

    @Test
    fun logoutAndLoggingInAgainStayTheirOwnRows() {
        val punches = listOf(
            Punch(t(9, 0), isIn = true),
            Punch(t(13, 0), isIn = false, isLogout = true),
            Punch(t(14, 0), isIn = true),
            Punch(t(18, 0), isIn = false, isLogout = true),
        )
        assertEquals(
            listOf(EntryRow.Login(punches[0]), EntryRow.Logout(punches[1]), EntryRow.Login(punches[2]), EntryRow.Logout(punches[3])),
            EntryRow.of(punches) { it },
        )
    }

    @Test
    fun editingTheDayKeepsBreakLabels() {
        val punches = listOf(
            Punch(t(8, 30), isIn = true),
            Punch(t(12, 30), isIn = false, label = "LUNCH"),
            Punch(t(13, 0), isIn = true),
            Punch(t(16, 0), isIn = false, label = "TEA"),
        )
        val shape = DayShape.of(punches)
        assertEquals(listOf("LUNCH", "TEA"), shape.breaks.map { it.label })
        assertEquals(punches, shape.toPunches())
    }

    @Test
    fun relabellingABreakIsWrittenDown() {
        val before = DayShape(t(9, 0), listOf(BreakSpan(t(12, 0), t(12, 30), "TEA")), null)
        val after = before.copy(breaks = listOf(BreakSpan(t(12, 0), t(12, 30), "LUNCH")))
        assertEquals("break Tea 12:00–12:30 → Lunch 12:00–12:30", after.describeChange(before) { "${it.hour}:%02d".format(it.minute) })
    }

    @Test
    fun extendAddsToTheLimit() {
        val plan = BreakPlan(LocalDate.of(2026, 10, 26), t(12, 30), Duration.ofMinutes(15))
        assertEquals(t(12, 50), plan.extended(Duration.ofMinutes(5)).end)
    }
}
