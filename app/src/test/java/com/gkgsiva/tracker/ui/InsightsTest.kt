package com.gkgsiva.tracker.ui

import com.gkgsiva.tracker.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class InsightsTest {
    private fun p(h: Int, m: Int, isIn: Boolean, logout: Boolean = false) = Punch(LocalTime.of(h, m), isIn, logout)

    @Test fun outlookCountsDaysNotPooledHours() {
        val mon = LocalDate.of(2026, 10, 19); val today = mon.plusDays(2) // Wed
        val days = mapOf(
            mon to DayCalculator.summarize(mon, listOf(p(8,0,true),p(17,30,false,true)), null), // 9.5h
            // Tue: nothing logged -> ignored
            today to DayCalculator.summarize(today, listOf(p(8,30,true)), LocalTime.of(12,30)), // 4h live
            mon.plusDays(3) to DayCalculator.summarize(mon.plusDays(3), emptyList(), null, type = DayType.LEAVE),
        )
        fun of(d: LocalDate) = days[d] ?: DayCalculator.summarize(d, emptyList(), null,
            type = DayType.effective(null, d, false, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        val o = Insights.outlook(mon, mon.plusDays(6), today, Rules(), ::of)
        // counted: Mon, Wed, Fri (Thu leave, Tue unlogged, Sat/Sun off)
        assertEquals(Duration.ofMinutes(810), o.done)
        assertEquals(1, o.daysMet)
        assertEquals(0, o.daysShort)
        assertEquals(2, o.daysLeft)
        assertEquals("2 working days left · 8h work, 9h in office each", o.paceText())
        assertEquals("1 of 1 day met 8h work, 9h in office", o.metText())
    }
    @Test fun forecastMessages() {
        val d = LocalDate.of(2026, 10, 21)
        val early = DayCalculator.summarize(d, emptyList(), LocalTime.of(8, 30))
        assertFalse(Insights.forecast(early, LocalTime.of(8, 30))!!.atRisk)
        val late = DayCalculator.summarize(d, emptyList(), LocalTime.of(9, 30))
        assertTrue(Insights.forecast(late, LocalTime.of(9, 30))!!.atRisk)
        val inside = DayCalculator.summarize(d, listOf(p(8,30,true)), LocalTime.of(10, 0))
        assertFalse(Insights.forecast(inside, LocalTime.of(10, 0))!!.atRisk)
    }
}
