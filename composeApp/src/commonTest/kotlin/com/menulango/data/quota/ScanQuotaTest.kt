package com.menulango.data.quota

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanQuotaTest {
    private class InMemoryStorage : QuotaStorage {
        override var month: String? = null
        override var used: Int = 0
    }

    private val storage = InMemoryStorage()
    private var month = "2026-09"
    private val quota = ScanQuota(storage, currentMonth = { month })

    @Test
    fun startsWithThreeFreeScans() {
        assertEquals(3, quota.quota.value.remaining)
        assertFalse(quota.quota.value.isExhausted)
    }

    @Test
    fun threeScansExhaustTheMonth() {
        repeat(3) { quota.recordScan() }

        assertEquals(0, quota.quota.value.remaining)
        assertTrue(quota.quota.value.isExhausted)
    }

    @Test
    fun remainingNeverGoesNegative() {
        repeat(5) { quota.recordScan() }
        assertEquals(0, quota.quota.value.remaining)
    }

    @Test
    fun newCalendarMonthResetsTheCount() {
        repeat(3) { quota.recordScan() }

        month = "2026-10"

        assertEquals(3, quota.refresh().remaining)
        assertEquals("2026-10", storage.month)
    }

    @Test
    fun scanRecordedAfterTheMonthTurnsCountsAgainstTheNewMonth() {
        repeat(3) { quota.recordScan() }
        month = "2026-10"

        quota.recordScan()

        assertEquals(2, quota.quota.value.remaining)
    }

    @Test
    fun countSurvivesARestart() {
        quota.recordScan()
        val afterRestart = ScanQuota(storage, currentMonth = { month })
        assertEquals(2, afterRestart.quota.value.remaining)
    }

    @Test
    fun sameMonthNumberInADifferentYearStillResets() {
        repeat(3) { quota.recordScan() }
        month = "2027-09"
        assertEquals(3, quota.refresh().remaining)
    }
}
