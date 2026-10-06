package com.wechatblocker.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class UsageTriggerLogicTest {

    @Test
    fun firstOpenAlwaysTriggers() {
        assertTrue(UsageTriggerLogic.shouldShowOnOpen(0L, 1_000L, 5))
    }

    @Test
    fun returnWithinAwayMinutesDoesNotTrigger() {
        val left = 10_000L
        val now = left + 2 * 60 * 1000L
        assertFalse(UsageTriggerLogic.shouldShowOnOpen(left, now, 5))
    }

    @Test
    fun returnAfterAwayMinutesTriggers() {
        val left = 10_000L
        val now = left + 6 * 60 * 1000L
        assertTrue(UsageTriggerLogic.shouldShowOnOpen(left, now, 5))
    }

    @Test
    fun nightTriggerWaitsForConfiguredTime() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 59)
        }
        assertFalse(
            UsageTriggerLogic.shouldShowNightReminder(cal, 23, 0, "", "2026-10-06")
        )
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 0)
        assertTrue(
            UsageTriggerLogic.shouldShowNightReminder(cal, 23, 0, "", "2026-10-06")
        )
        assertFalse(
            UsageTriggerLogic.shouldShowNightReminder(cal, 23, 0, "2026-10-06", "2026-10-06")
        )
    }
}
