package com.localbank.finance.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateRangesTest {

    private fun cal(year: Int, month: Int, day: Int, hour: Int = 12): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }

    @Test
    fun `month cobre do primeiro ao ultimo milissegundo`() {
        val range = DateRanges.month(cal(2026, Calendar.FEBRUARY, 15))

        assertEquals(cal(2026, Calendar.FEBRUARY, 1, 0).timeInMillis, range.from)
        assertEquals(cal(2026, Calendar.MARCH, 1, 0).timeInMillis - 1, range.to)
    }

    @Test
    fun `month inclui o fim do ultimo dia`() {
        val range = DateRanges.month(cal(2026, Calendar.JANUARY, 31))
        val lastSecond = cal(2026, Calendar.JANUARY, 31, 23).apply {
            set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }

        assertTrue(lastSecond.timeInMillis in range)
        assertFalse(cal(2026, Calendar.FEBRUARY, 1, 0).timeInMillis in range)
    }

    @Test
    fun `semester no dia 31 nao rola para o mes seguinte`() {
        // 31/08 + set(MONTH, JULY) funcionaria, mas 31/03 + set(MONTH, FEBRUARY) viraria março
        val second = DateRanges.semester(cal(2026, Calendar.AUGUST, 31))
        assertEquals(cal(2026, Calendar.JULY, 1, 0).timeInMillis, second.from)
        assertEquals(cal(2027, Calendar.JANUARY, 1, 0).timeInMillis - 1, second.to)

        val first = DateRanges.semester(cal(2026, Calendar.MARCH, 31))
        assertEquals(cal(2026, Calendar.JANUARY, 1, 0).timeInMillis, first.from)
        assertEquals(cal(2026, Calendar.JULY, 1, 0).timeInMillis - 1, first.to)
    }

    @Test
    fun `monthKey usa zero a esquerda`() {
        assertEquals("2026-03", DateRanges.monthKey(cal(2026, Calendar.MARCH, 10)))
        assertEquals("2026-12", DateRanges.monthKey(cal(2026, Calendar.DECEMBER, 10)))
    }

    @Test
    fun `monthIndex e continuo entre anos`() {
        val dec = DateRanges.monthIndex(cal(2025, Calendar.DECEMBER, 1))
        val jan = DateRanges.monthIndex(cal(2026, Calendar.JANUARY, 1))
        assertEquals(1, jan - dec)
    }
}
