package com.gkgsiva.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class DayCalculatorTest {

    private val day = LocalDate.of(2026, 10, 22)

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)
    private fun inAt(h: Int, m: Int) = Punch(t(h, m), isIn = true)
    private fun outAt(h: Int, m: Int) = Punch(t(h, m), isIn = false)
    private fun logoutAt(h: Int, m: Int) = Punch(t(h, m), isIn = false, isLogout = true)

    @Test
    fun noPunchesIsNotStarted() {
        val s = DayCalculator.summarize(day, emptyList(), now = t(10, 0))
        assertEquals(DayStatus.NOT_STARTED, s.status)
        assertEquals(Duration.ofHours(8), s.remaining)
        assertFalse(s.worked)
    }

    @Test
    fun insideCountsLiveUntilNow() {
        val s = DayCalculator.summarize(day, listOf(inAt(8, 30)), now = t(10, 0))
        assertEquals(DayStatus.INSIDE, s.status)
        assertEquals(Duration.ofMinutes(90), s.inside)
        assertEquals(Duration.ofMinutes(390), s.remaining)
        assertEquals(t(17, 0), s.expectedLeave) // 10:00 + 6.5h = 16:30, but 5 PM rule wins
        assertFalse(s.lateArrival)
    }

    @Test
    fun breaksAreGapsBetweenCheckOutAndCheckIn() {
        val punches = listOf(inAt(8, 30), outAt(12, 0), inAt(12, 40), outAt(15, 0), inAt(15, 10))
        val s = DayCalculator.summarize(day, punches, now = t(16, 0))
        assertEquals(DayStatus.INSIDE, s.status)
        assertEquals(Duration.ofMinutes(50), s.breaks)
        assertEquals(2, s.breakCount)
        assertEquals(Duration.ofMinutes(210 + 140 + 50), s.inside)
        assertEquals(Duration.ofMinutes(10), s.breakLeft)
        assertEquals(t(17, 20), s.expectedLeave)
    }

    @Test
    fun ongoingBreakCountsLiveAndGivesReturnBy() {
        val punches = listOf(inAt(8, 0), outAt(12, 0))
        val s = DayCalculator.summarize(day, punches, now = t(12, 20))
        assertEquals(DayStatus.ON_BREAK, s.status)
        assertEquals(Duration.ofMinutes(20), s.breaks)
        // Logged in at 8:00, so 1h30m of break still lets you leave by 5:30.
        assertEquals(Duration.ofMinutes(90), s.breakAllowance)
        assertEquals(t(13, 30), s.returnBy)
        assertEquals(Duration.ofHours(4), s.remaining)
    }

    @Test
    fun exceedingBreakAllowanceIsFlagged() {
        val punches = listOf(inAt(8, 0), outAt(12, 0))
        val s = DayCalculator.summarize(day, punches, now = t(13, 45))
        assertTrue(s.breakExceeded)
        assertEquals(Duration.ofMinutes(-15), s.breakLeft)
        assertNull(s.returnBy)
    }

    @Test
    fun breakAllowanceFollowsLoginTime() {
        val rules = Rules()
        assertEquals(Duration.ofHours(1), rules.breakAllowance(null))
        assertEquals(Duration.ofHours(1), rules.breakAllowance(t(8, 30)))
        assertEquals(Duration.ofMinutes(90), rules.breakAllowance(t(8, 0)))
        assertEquals(Duration.ofMinutes(30), rules.breakAllowance(t(9, 0)))
        assertEquals(Duration.ZERO, rules.breakAllowance(t(9, 45)))
    }

    @Test
    fun longBreakIsNotLop() {
        val punches = listOf(inAt(8, 30), outAt(12, 0), inAt(13, 30), logoutAt(18, 0))
        val s = DayCalculator.summarize(day, punches, now = t(18, 5))
        assertTrue(s.breakExceeded)
        assertTrue(s.targetMet)
        assertFalse(s.lop)
    }

    @Test
    fun regularizedDayKeepsReasonsButIsNotLop() {
        val punches = listOf(inAt(9, 12), logoutAt(17, 45))
        val lop = DayCalculator.summarize(day, punches, now = null)
        assertTrue(lop.lop)
        val s = DayCalculator.summarize(day, punches, now = null, regularized = "Manager approved")
        assertFalse(s.lop)
        assertTrue(s.isRegularized)
        assertEquals(listOf(LopReason.LATE_LOGIN), s.lopReasons)
    }

    @Test
    fun logoutEndsTheDay() {
        val punches = listOf(inAt(8, 0), outAt(12, 0), inAt(12, 30), logoutAt(17, 0))
        val s = DayCalculator.summarize(day, punches, now = t(18, 0))
        assertEquals(DayStatus.DONE, s.status)
        assertEquals(Duration.ofMinutes(30), s.breaks)
        assertEquals(Duration.ofMinutes(510), s.inside)
        assertTrue(s.targetMet)
        assertFalse(s.earlyLeave)
    }

    @Test
    fun breakAfter8HoursIsStillABreakUntilLogout() {
        val punches = listOf(inAt(8, 0), outAt(16, 30))
        val s = DayCalculator.summarize(day, punches, now = t(16, 45))
        assertEquals(DayStatus.ON_BREAK, s.status)
        assertEquals(Duration.ofMinutes(15), s.breaks)
    }

    @Test
    fun earlyLogoutIsLopLive() {
        val s = DayCalculator.summarize(day, listOf(inAt(8, 0), logoutAt(16, 30)), now = t(16, 45))
        assertEquals(DayStatus.DONE, s.status)
        assertEquals(listOf(LopReason.EARLY_EXIT), s.lopReasons)
    }

    @Test
    fun pastDayTrailingCheckoutIsNotABreak() {
        val punches = listOf(inAt(9, 15), outAt(16, 30))
        val s = DayCalculator.summarize(day, punches, now = null)
        assertEquals(DayStatus.DONE, s.status)
        assertEquals(Duration.ZERO, s.breaks)
        assertTrue(s.lateArrival)
        assertTrue(s.earlyLeave)
        assertFalse(s.targetMet)
    }

    @Test
    fun pastDayWithoutCheckoutIsFlagged() {
        val s = DayCalculator.summarize(day, listOf(inAt(8, 0)), now = null)
        assertEquals(DayStatus.MISSING_CHECKOUT, s.status)
        assertEquals(Duration.ZERO, s.inside)
    }

    @Test
    fun unsortedAndDuplicatePunchesAreHandled() {
        val punches = listOf(outAt(12, 0), inAt(8, 0), inAt(8, 5), inAt(13, 0))
        val s = DayCalculator.summarize(day, punches, now = t(14, 0))
        assertEquals(t(8, 0), s.firstIn)
        assertEquals(Duration.ofHours(1), s.breaks)
        assertEquals(Duration.ofHours(5), s.inside)
    }

    @Test
    fun weeklyPeriodsAggregateWorkedDays() {
        val mon = LocalDate.of(2026, 10, 19)
        val days = listOf(
            DayCalculator.summarize(mon, listOf(inAt(8, 0), outAt(17, 0)), null),
            DayCalculator.summarize(mon.plusDays(1), listOf(inAt(9, 30), outAt(16, 30)), null),
        ).associateBy { it.date }
        val weeks = Periods.weekly(days, today = mon.plusDays(3), count = 2)
        assertEquals(2, weeks.size)
        assertEquals(0, weeks[0].daysWorked)
        val w = weeks[1]
        assertEquals(mon, w.start)
        assertEquals(2, w.daysWorked)
        assertEquals(Duration.ofHours(16), w.totalInside)
        assertEquals(Duration.ofHours(8), w.avgInside)
        assertEquals(1, w.lateDays)
        assertEquals(1, w.shortDays)
        assertEquals(1, w.lopDays)
    }

    @Test
    fun wfhUsesOnlyFirstLoginAndLastLogout() {
        val punches = listOf(inAt(8, 30), outAt(12, 0), inAt(13, 0), logoutAt(17, 0))
        val s = DayCalculator.summarize(day, punches, now = null, type = DayType.WFH)
        assertEquals(Duration.ofMinutes(510), s.inside)
        assertEquals(Duration.ZERO, s.breaks)
        assertEquals(0, s.breakCount)
        assertFalse(s.lop)
        assertTrue(s.wfh)
    }

    @Test
    fun wfhIsLiveUntilLogoutAndFollowsLopRules() {
        val live = DayCalculator.summarize(day, listOf(inAt(9, 10)), now = t(12, 0), type = DayType.WFH)
        assertEquals(DayStatus.INSIDE, live.status)
        assertEquals(Duration.ofMinutes(170), live.inside)
        assertEquals(listOf(LopReason.LATE_LOGIN), live.lopReasons)

        val relogin = DayCalculator.summarize(day, listOf(inAt(8, 0), logoutAt(15, 0), inAt(15, 30)), now = t(16, 0), type = DayType.WFH)
        assertEquals(DayStatus.INSIDE, relogin.status)
        assertEquals(Duration.ofHours(8), relogin.inside)
    }

    @Test
    fun holidayAndLeaveNeedNoLoginAndAreNeverLop() {
        for (type in listOf(DayType.HOLIDAY, DayType.LEAVE)) {
            val s = DayCalculator.summarize(day, listOf(inAt(10, 0), logoutAt(12, 0)), now = t(13, 0), type = type)
            assertEquals(DayStatus.DAY_OFF, s.status)
            assertFalse(s.worked)
            assertFalse(s.lop)
            assertEquals(Duration.ZERO, s.remaining)
            assertEquals(Duration.ZERO, s.inside)
        }
        val mon = LocalDate.of(2026, 10, 19)
        val days = listOf(
            DayCalculator.summarize(mon, emptyList(), null, type = DayType.HOLIDAY),
            DayCalculator.summarize(mon.plusDays(1), emptyList(), null, type = DayType.LEAVE),
            DayCalculator.summarize(mon.plusDays(2), listOf(inAt(9, 30), logoutAt(17, 0)), null),
        ).associateBy { it.date }
        val w = Periods.summarize(days, mon, mon.plusDays(6))
        assertEquals(1, w.holidays)
        assertEquals(1, w.leaveDays)
        assertEquals(1, w.daysWorked)
        assertEquals(1, w.lopDays)
    }

    @Test
    fun weeklyOffAppliesOnlyWhenNothingIsLogged() {
        val sunday = LocalDate.of(2026, 10, 25)
        val offs = setOf(java.time.DayOfWeek.SUNDAY)
        assertEquals(DayType.WEEKLY_OFF, DayType.effective(null, sunday, hasPunches = false, weeklyOffs = offs))
        assertEquals(DayType.OFFICE, DayType.effective(null, sunday, hasPunches = true, weeklyOffs = offs))
        assertEquals(DayType.LEAVE, DayType.effective(DayType.LEAVE, sunday, hasPunches = false, weeklyOffs = offs))
        assertEquals(DayType.OFFICE, DayType.effective(null, sunday.minusDays(1), hasPunches = false, weeklyOffs = offs))
        assertTrue(DayType.WEEKLY_OFF.isDayOff)
    }

    @Test
    fun customRulesAreUsed() {
        val rules = Rules(arriveBy = t(9, 30), leaveAfter = t(18, 0), requiredInside = Duration.ofMinutes(510))
        val s = DayCalculator.summarize(day, listOf(inAt(9, 20), logoutAt(18, 0)), null, rules)
        assertFalse(s.lop)
        val short = DayCalculator.summarize(day, listOf(inAt(9, 20), logoutAt(17, 45)), null, rules)
        assertEquals(listOf(LopReason.EARLY_EXIT, LopReason.UNDER_8H), short.lopReasons)
    }

    @Test
    fun onTimeFullDayIsNotLop() {
        val s = DayCalculator.summarize(day, listOf(inAt(8, 30), outAt(13, 0), inAt(13, 30), outAt(17, 5)), null)
        assertFalse(s.lop)
    }

    @Test
    fun lateLoginIsLopEvenWithMoreThan8Hours() {
        val s = DayCalculator.summarize(day, listOf(inAt(9, 5), outAt(18, 30)), null)
        assertTrue(s.targetMet)
        assertEquals(listOf(LopReason.LATE_LOGIN), s.lopReasons)
    }

    @Test
    fun earlyExitIsLopEvenWithMoreThan8Hours() {
        val s = DayCalculator.summarize(day, listOf(inAt(7, 30), outAt(16, 45)), null)
        assertTrue(s.targetMet)
        assertEquals(listOf(LopReason.EARLY_EXIT), s.lopReasons)
    }

    @Test
    fun under8HoursIsLopEvenWhenOnTime() {
        val s = DayCalculator.summarize(day, listOf(inAt(8, 55), outAt(12, 0), inAt(13, 30), outAt(17, 0)), null)
        assertEquals(listOf(LopReason.UNDER_8H), s.lopReasons)
    }

    @Test
    fun lateLoginShowsAsLopLive() {
        val s = DayCalculator.summarize(day, listOf(inAt(9, 1)), now = t(10, 0))
        assertEquals(listOf(LopReason.LATE_LOGIN), s.lopReasons)
        assertFalse(DayCalculator.summarize(day, listOf(inAt(8, 59)), now = t(10, 0)).lop)
    }
}
