package com.gkgsiva.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class DayShapeTest {

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)
    private val fmt: (LocalTime) -> String = { "%d:%02d".format(it.hour, it.minute) }

    @Test
    fun readsLoginBreaksAndLogout() {
        val punches = listOf(
            Punch(t(8, 50), true), Punch(t(13, 0), false), Punch(t(13, 40), true), Punch(t(17, 45), false, isLogout = true),
        )
        val shape = DayShape.of(punches)
        assertEquals(DayShape(t(8, 50), listOf(BreakSpan(t(13, 0), t(13, 40))), t(17, 45)), shape)
        assertEquals(punches, shape.toPunches())
    }

    @Test
    fun logoutFollowedByLoginIsABreak() {
        val shape = DayShape.of(
            listOf(Punch(t(8, 50), true), Punch(t(12, 0), false, isLogout = true), Punch(t(12, 30), true), Punch(t(18, 0), false, isLogout = true)),
        )
        assertEquals(listOf(BreakSpan(t(12, 0), t(12, 30))), shape.breaks)
        assertEquals(t(18, 0), shape.logout)
    }

    @Test
    fun trailingBreakStaysOpen() {
        val shape = DayShape.of(listOf(Punch(t(8, 50), true), Punch(t(13, 0), false)))
        assertEquals(listOf(BreakSpan(t(13, 0), null)), shape.breaks)
        assertNull(shape.logout)
        assertNull(shape.problem())
    }

    @Test
    fun problemsCatchTimesOutOfOrder() {
        assertNotNull(DayShape(t(9, 0), listOf(BreakSpan(t(8, 0), t(8, 30))), null).problem())
        assertNotNull(DayShape(t(9, 0), listOf(BreakSpan(t(13, 0), t(12, 0))), null).problem())
        assertNotNull(DayShape(t(9, 0), emptyList(), t(8, 0)).problem())
        assertNotNull(DayShape(t(9, 0), listOf(BreakSpan(t(13, 0), null)), t(18, 0)).problem())
        assertNotNull(DayShape(null, emptyList(), t(18, 0)).problem())
        assertNull(DayShape(null, emptyList(), null).problem())
    }

    @Test
    fun describesWhatChanged() {
        val before = DayShape(t(9, 5), listOf(BreakSpan(t(13, 0), t(13, 30))), null)
        val after = DayShape(t(8, 55), listOf(BreakSpan(t(13, 0), t(13, 45))), t(17, 40))
        assertEquals(
            "login 9:05 → 8:55; break 13:00–13:30 → 13:00–13:45; logout 17:40 added",
            after.describeChange(before, fmt),
        )
        assertEquals("no change", before.describeChange(before, fmt))
    }
}
