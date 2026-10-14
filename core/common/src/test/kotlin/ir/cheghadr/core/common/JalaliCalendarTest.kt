package ir.cheghadr.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JalaliCalendarTest {

    // ------------------------------------------------------------- known dates

    @Test
    fun `known conversions nowruz`() {
        assertEquals(
            JalaliCalendar.JalaliDate(1403, 1, 1),
            JalaliCalendar.toJalali(LocalDate.of(2024, 3, 20)),
        )
        assertEquals(
            JalaliCalendar.JalaliDate(1402, 1, 1),
            JalaliCalendar.toJalali(LocalDate.of(2023, 3, 21)),
        )
        assertEquals(
            JalaliCalendar.JalaliDate(1404, 1, 1),
            JalaliCalendar.toJalali(LocalDate.of(2025, 3, 21)),
        )
        assertEquals(
            JalaliCalendar.JalaliDate(1400, 1, 1),
            JalaliCalendar.toJalali(LocalDate.of(2021, 3, 21)),
        )
        assertEquals(
            JalaliCalendar.JalaliDate(1399, 1, 1),
            JalaliCalendar.toJalali(LocalDate.of(2020, 3, 20)),
        )
    }

    @Test
    fun `known conversion revolution day`() {
        // 22 Bahman 1357 = 11 February 1979
        assertEquals(
            JalaliCalendar.JalaliDate(1357, 11, 22),
            JalaliCalendar.toJalali(LocalDate.of(1979, 2, 11)),
        )
    }

    @Test
    fun `known conversion mid year`() {
        // 1 Tir 1403 = 21 June 2024
        assertEquals(
            JalaliCalendar.JalaliDate(1403, 4, 1),
            JalaliCalendar.toJalali(LocalDate.of(2024, 6, 21)),
        )
        // 1 Dey 1403 = 21 December 2024
        assertEquals(
            JalaliCalendar.JalaliDate(1403, 10, 1),
            JalaliCalendar.toJalali(LocalDate.of(2024, 12, 21)),
        )
        assertEquals(
            JalaliCalendar.JalaliDate(1403, 10, 2),
            JalaliCalendar.toJalali(LocalDate.of(2024, 12, 22)),
        )
    }

    @Test
    fun `leap year esfand 30`() {
        // 1403 is a leap year: 30 Esfand 1403 = 20 March 2025
        assertEquals(
            JalaliCalendar.JalaliDate(1403, 12, 30),
            JalaliCalendar.toJalali(LocalDate.of(2025, 3, 20)),
        )
        assertTrue(JalaliCalendar.isLeapYear(1403))
        assertFalse(JalaliCalendar.isLeapYear(1404))
        assertTrue(JalaliCalendar.isLeapYear(1399))
        assertFalse(JalaliCalendar.isLeapYear(1402))
        assertEquals(30, JalaliCalendar.monthLength(1403, 12))
        assertEquals(29, JalaliCalendar.monthLength(1404, 12))
    }

    // ------------------------------------------------------------- round trips

    @Test
    fun `round trip every day across two centuries`() {
        var date = LocalDate.of(1925, 1, 1)
        val end = LocalDate.of(2125, 12, 31)
        var count = 0
        while (!date.isAfter(end)) {
            val j = JalaliCalendar.toJalali(date)
            val back = JalaliCalendar.toGregorian(j)
            assertEquals("round-trip failed for $date → $j", date, back)
            // Month/day bounds hold.
            assertTrue(j.month in 1..12)
            assertTrue(j.day in 1..JalaliCalendar.monthLength(j.year, j.month))
            date = date.plusDays(1)
            count++
        }
        assertTrue(count > 70_000)
    }

    @Test
    fun `year length matches sum of month lengths`() {
        for (jy in 1300..1500) {
            val expected = if (JalaliCalendar.isLeapYear(jy)) 366 else 365
            val actual = (1..12).map { JalaliCalendar.monthLength(jy, it).toLong() }.sum()
            assertEquals("year length mismatch for $jy", expected.toLong(), actual)
        }
    }

    @Test
    fun `nowruz is always in march`() {
        for (jy in 1310..1470) {
            val nowruz = JalaliCalendar.toGregorian(JalaliCalendar.JalaliDate(jy, 1, 1))
            assertEquals(3, nowruz.monthValue)
            assertTrue("nowruz day out of range for $jy", nowruz.dayOfMonth in 19..22)
            // The day before Nowruz is the last day of Esfand.
            val lastEsfand = nowruz.minusDays(1)
            val jLast = JalaliCalendar.toJalali(lastEsfand)
            assertEquals(12, jLast.month)
            assertEquals(JalaliCalendar.monthLength(jy - 1, 12), jLast.day)
        }
    }

    // ------------------------------------------------------------- formatting

    @Test
    fun `formatting helpers`() {
        val j = JalaliCalendar.JalaliDate(1403, 7, 22)
        assertEquals("1403/07/22", JalaliCalendar.formatNumeric(j))
        assertEquals("22 مهر 1403", JalaliCalendar.formatLongFa(j))
        assertEquals("شنبه", JalaliCalendar.weekdayNameFa(LocalDate.of(2024, 10, 5)))
        assertEquals("جمعه", JalaliCalendar.weekdayNameFa(LocalDate.of(2024, 10, 4)))
    }
}
