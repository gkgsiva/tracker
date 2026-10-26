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
        assertEquals(Duration.ofHours(9), s.remaining) // 9h in office is the longer of the two
        assertFalse(s.worked)
    }

    @Test
    fun insideCountsLiveUntilNow() {
        val s = DayCalculator.summarize(day, listOf(inAt(8, 30)), now = t(10, 0))
        assertEquals(DayStatus.INSIDE, s.status)
        assertEquals(Duration.ofMinutes(90), s.inside)
        assertEquals(Duration.ofMinutes(450), s.remaining)
        assertEquals(t(17, 30), s.expectedLeave) // 6.5h more work ends 16:30, but 9h in office ends 17:30
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
        // 8h of work would be done at 17:20, but 9h in office from 8:30 is 17:30.
        assertEquals(t(17, 30), s.expectedLeave)
    }

    @Test
    fun ongoingBreakCountsLiveAndGivesReturnBy() {
        val punches = listOf(inAt(8, 0), outAt(12, 0))
        val s = DayCalculator.summarize(day, punches, now = t(12, 20))
        assertEquals(DayStatus.ON_BREAK, s.status)
        assertEquals(Duration.ofMinutes(20), s.breaks)
        // 9h in office from 8:00 minus 8h of work leaves 1h of break.
        assertEquals(Duration.ofMinutes(60), s.breakAllowance)
        assertEquals(t(13, 0), s.returnBy)
        assertEquals(Duration.ofHours(4), s.inside)
    }

    @Test
    fun exceedingBreakAllowanceIsFlagged() {
        val punches = listOf(inAt(8, 0), outAt(12, 0))
        val s = DayCalculator.summarize(day, punches, now = t(13, 45))
        assertTrue(s.breakExceeded)
        assertEquals(Duration.ofMinutes(-45), s.breakLeft)
        assertNull(s.returnBy)
    }

    @Test
    fun breakAllowanceFollowsLoginTime() {
        val rules = Rules()
        assertEquals(Duration.ofHours(1), rules.breakAllowance(null))
        assertEquals(Duration.ofHours(1), rules.breakAllowance(t(8, 30)))
        assertEquals(Duration.ofHours(1), rules.breakAllowance(t(8, 0)))
        // Before 8:00, 5 PM comes later than login + 9h, so there's more room.
        assertEquals(Duration.ofMinutes(90), rules.breakAllowance(t(7, 30)))
        assertEquals(Duration.ofHours(1), rules.breakAllowance(t(9, 0)))
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
        val punches = listOf(inAt(9, 12), logoutAt(18, 15))
        val lop = DayCalculator.summarize(day, punches, now = null)
        assertTrue(lop.lop)
        val s = DayCalculator.summarize(day, punches, now = null, regularized = "Manager approved")
        assertFalse(s.lop)
        assertTrue(s.isRegularized)
        assertEquals(listOf(LopReason.LATE_LOGIN), s.lopReasons)
    }

    @Test
    fun badEntriesAreFlagged() {
        val punches = listOf(outAt(8, 0), inAt(8, 30), inAt(9, 0), outAt(12, 0), outAt(12, 5), inAt(12, 30), logoutAt(17, 30))
        val s = DayCalculator.summarize(day, punches, now = null)
        assertEquals(
            listOf(
                EntryIssue(EntryIssueKind.OUT_BEFORE_LOGIN, t(8, 0)),
                EntryIssue(EntryIssueKind.DOUBLE_IN, t(9, 0)),
                EntryIssue(EntryIssueKind.DOUBLE_OUT, t(12, 5)),
            ),
            s.issues,
        )
        assertEquals(Duration.ofMinutes(30), s.breaks)
    }

    @Test
    fun pastDayEndingOnABreakIsFlagged() {
        val s = DayCalculator.summarize(day, listOf(inAt(8, 30), outAt(17, 40)), now = null)
        assertEquals(listOf(EntryIssue(EntryIssueKind.OPEN_BREAK, t(17, 40))), s.issues)
        val ok = DayCalculator.summarize(day, listOf(inAt(8, 30), logoutAt(17, 40)), now = null)
        assertTrue(ok.issues.isEmpty())
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
        val s = DayCalculator.summarize(day, listOf(inAt(7, 30), logoutAt(16, 30)), now = t(16, 45))
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
        val punches = listOf(inAt(8, 30), outAt(12, 0), inAt(13, 0), logoutAt(17, 30))
        val s = DayCalculator.summarize(day, punches, now = null, type = DayType.WFH)
        assertEquals(Duration.ofMinutes(540), s.inside)
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
        val rules = Rules(arriveBy = t(9, 30), leaveAfter = t(18, 0), requiredInside = Duration.ofMinutes(510), requiredPresent = Duration.ofMinutes(510))
        val s = DayCalculator.summarize(day, listOf(inAt(9, 20), logoutAt(18, 0)), null, rules)
        assertFalse(s.lop)
        val short = DayCalculator.summarize(day, listOf(inAt(9, 20), logoutAt(17, 45)), null, rules)
        assertEquals(listOf(LopReason.EARLY_EXIT, LopReason.SHORT_WORK), short.lopReasons)
    }

    @Test
    fun onTimeFullDayIsNotLop() {
        val s = DayCalculator.summarize(day, listOf(inAt(8, 30), outAt(13, 0), inAt(13, 30), outAt(17, 30)), null)
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
        // 6h35m of work and 8h05m in the office: both rules missed, and both named.
        assertEquals(listOf(LopReason.SHORT_WORK, LopReason.SHORT_PRESENT), s.lopReasons)
    }

    @Test
    fun lateLoginShowsAsLopLive() {
        val s = DayCalculator.summarize(day, listOf(inAt(9, 1)), now = t(10, 0))
        assertEquals(listOf(LopReason.LATE_LOGIN), s.lopReasons)
        assertFalse(DayCalculator.summarize(day, listOf(inAt(8, 59)), now = t(10, 0)).lop)
    }

    @Test
    fun halfDayNeedsOnlyItsHoursAtAnyTime() {
        val s = DayCalculator.summarize(day, listOf(inAt(13, 0), logoutAt(17, 5)), null, type = DayType.HALF_DAY)
        assertTrue(s.targetMet)
        assertFalse(s.lop)
        val short = DayCalculator.summarize(day, listOf(inAt(9, 30), logoutAt(13, 0)), null, type = DayType.HALF_DAY)
        assertEquals(listOf(LopReason.SHORT_WORK), short.lopReasons)
        assertEquals(Duration.ofHours(4), short.rules.requiredInside)
    }

    @Test
    fun shortLeaveEasesCoreHoursAndHours() {
        // 2h short leave: 6h of work, 7h in office, log in by 11:00, leave after 15:00.
        val late = DayCalculator.summarize(day, listOf(inAt(10, 45), logoutAt(17, 45)), null, type = DayType.SHORT_LEAVE)
        assertFalse(late.lop)
        val early = DayCalculator.summarize(day, listOf(inAt(8, 0), logoutAt(15, 0)), null, type = DayType.SHORT_LEAVE)
        assertFalse(early.lop)
        val leftAt5 = DayCalculator.summarize(day, listOf(inAt(10, 45), logoutAt(17, 0)), null, type = DayType.SHORT_LEAVE)
        assertEquals(listOf(LopReason.SHORT_PRESENT), leftAt5.lopReasons)
        val tooLate = DayCalculator.summarize(day, listOf(inAt(11, 5), logoutAt(18, 30)), null, type = DayType.SHORT_LEAVE)
        assertEquals(listOf(LopReason.LATE_LOGIN), tooLate.lopReasons)
        assertEquals(Duration.ofHours(1), early.breakAllowance)
    }

    @Test
    fun breakPlanAppliesOnlyToTheBreakInProgress() {
        val onBreak = DayCalculator.summarize(day, listOf(inAt(8, 30), outAt(13, 0)), now = t(13, 10))
        val plan = BreakPlan(day, t(13, 0), Duration.ofMinutes(15))
        assertTrue(plan.appliesTo(onBreak))
        assertEquals(t(13, 15), plan.end)
        assertEquals(Duration.ofMinutes(5), plan.left(t(13, 10)))
        val back = DayCalculator.summarize(day, listOf(inAt(8, 30), outAt(13, 0), inAt(13, 12)), now = t(13, 20))
        assertFalse(plan.appliesTo(back))
        val nextBreak = DayCalculator.summarize(day, listOf(inAt(8, 30), outAt(13, 0), inAt(13, 12), outAt(15, 0)), now = t(15, 5))
        assertFalse(plan.appliesTo(nextBreak))
    }

    @Test
    fun yearSplitsIntoMonthsWithCounts() {
        val jan = LocalDate.of(2026, 1, 5)
        val days = mapOf(
            jan to DayCalculator.summarize(jan, listOf(inAt(8, 30), logoutAt(17, 30)), null),
            jan.plusDays(1) to DayCalculator.summarize(jan.plusDays(1), listOf(inAt(9, 30), logoutAt(18, 30)), null, regularized = ""),
            jan.plusDays(2) to DayCalculator.summarize(jan.plusDays(2), emptyList(), null, type = DayType.LEAVE),
            jan.plusDays(3) to DayCalculator.summarize(jan.plusDays(3), listOf(inAt(13, 0), logoutAt(17, 0)), null, type = DayType.HALF_DAY),
        )
        val year = Periods.year(days, 2026)
        assertEquals(12, year.size)
        val j = year.first()
        assertEquals(3, j.metDays)
        assertEquals(0, j.lopDays)
        assertEquals(1, j.regularizedDays)
        assertEquals(1, j.leaveDays)
        assertEquals(1, j.halfDays)
        assertEquals(0, year[1].daysWorked)
    }

    @Test
    fun leaveTimeIsLoginPlusNineHoursNotFivePm() {
        // Siva's day: in at 8:18, 33m of break so far. 5 PM would leave only 8h42m in the office.
        val s = DayCalculator.summarize(day, listOf(inAt(8, 18), outAt(10, 0), inAt(10, 20), outAt(11, 0), inAt(11, 13)), now = t(11, 42))
        assertEquals(Duration.ofMinutes(33), s.breaks)
        assertEquals(t(17, 18), s.expectedLeave)
        assertEquals(Duration.ofMinutes(9 * 60 - 204), s.remaining)
        val leftAt5 = DayCalculator.summarize(day, listOf(inAt(8, 18), outAt(12, 0), inAt(12, 30), logoutAt(17, 0)), null)
        assertTrue(leftAt5.workMet)
        assertEquals(listOf(LopReason.SHORT_PRESENT), leftAt5.lopReasons)
    }

    @Test
    fun longerBreakPushesLeaveTimeByWorkHours() {
        // 1h30m of break: 8h of work needs until 17:48, later than login + 9h.
        val s = DayCalculator.summarize(day, listOf(inAt(8, 18), outAt(12, 0), inAt(13, 30)), now = t(14, 0))
        assertEquals(t(17, 48), s.expectedLeave)
    }

    @Test
    fun wfhNeedsNineHoursToo() {
        val short = DayCalculator.summarize(day, listOf(inAt(8, 30), logoutAt(17, 0)), null, type = DayType.WFH)
        assertEquals(listOf(LopReason.SHORT_PRESENT), short.lopReasons)
        val full = DayCalculator.summarize(day, listOf(inAt(8, 30), logoutAt(17, 30)), null, type = DayType.WFH)
        assertFalse(full.lop)
    }
}
