package ir.cheghadr.core.common

import java.time.LocalDate

/**
 * Jalali (Solar Hijri) ↔ Gregorian conversion.
 *
 * Port of the well-known jalaali algorithm (MIT, https://github.com/jalaali/jalaali-js)
 * which is the de-facto standard used by Persian calendar libraries. The port is
 * covered by exhaustive unit tests: known reference dates, leap years, month
 * lengths and a full day-by-day round-trip across two centuries.
 */
object JalaliCalendar {

    data class JalaliDate(val year: Int, val month: Int, val day: Int) {
        override fun toString(): String = "$year-$month-$day"
    }

    // Gregorian years in which the 33-year Jalali leap cycle breaks.
    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    val monthNamesFa = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    val weekdayNamesFa = listOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه",
    )

    /** True when [jy] is a Jalali leap year (Esfand has 30 days). */
    fun isLeapYear(jy: Int): Boolean = jalCal(jy).leap == 0

    /** Number of days in a Jalali month. */
    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        isLeapYear(jy) -> 30
        else -> 29
    }

    /** Converts a Gregorian date to the Jalali calendar. */
    fun toJalali(date: LocalDate): JalaliDate {
        val jdn = g2d(date.year, date.monthValue, date.dayOfMonth)
        return d2j(jdn)
    }

    /** Converts a Jalali date to the Gregorian calendar. */
    fun toGregorian(jdate: JalaliDate): LocalDate {
        val jdn = j2d(jdate.year, jdate.month, jdate.day)
        val g = d2g(jdn)
        return LocalDate.of(g.first, g.second, g.third)
    }

    /** Short numeric form, e.g. 1403/07/22 (no digit localization here). */
    fun formatNumeric(jdate: JalaliDate): String =
        "%04d/%02d/%02d".format(jdate.year, jdate.month, jdate.day)

    /** Long Persian form, e.g. «۲۲ مهر ۱۴۰۳» (digits localized by the caller). */
    fun formatLongFa(jdate: JalaliDate): String =
        "${jdate.day} ${monthNamesFa[jdate.month - 1]} ${jdate.year}"

    /** Persian weekday name for a date. */
    fun weekdayNameFa(date: LocalDate): String = when (date.dayOfWeek) {
        java.time.DayOfWeek.SATURDAY -> weekdayNamesFa[0]
        java.time.DayOfWeek.SUNDAY -> weekdayNamesFa[1]
        java.time.DayOfWeek.MONDAY -> weekdayNamesFa[2]
        java.time.DayOfWeek.TUESDAY -> weekdayNamesFa[3]
        java.time.DayOfWeek.WEDNESDAY -> weekdayNamesFa[4]
        java.time.DayOfWeek.THURSDAY -> weekdayNamesFa[5]
        java.time.DayOfWeek.FRIDAY -> weekdayNamesFa[6]
    }

    // ---------------------------------------------------------------- internals

    private class JalCalResult(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): JalCalResult {
        require(jy >= breaks.first() && jy < breaks.last()) { "Jalali year out of range: $jy" }
        var leapJ = -14
        var jp = breaks.first()
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1

        val gy = jy + 621
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG

        if (jump - n < 6) n = n - jump + (jump + 4) / 33 * 33
        var leap = ((n + 1) % 33 - 1) % 4
        if (leap == -1) leap = 4
        return JalCalResult(leap, gy, march)
    }

    /** Julian Day Number of a Gregorian date (jalaali algorithm). */
    private fun g2d(gy: Int, gm: Int, gd: Int): Long {
        var d = ((gy + (gm - 8) / 6 + 100100).toLong() * 1461L) / 4 +
            (153L * ((gm + 9) % 12) + 2) / 5 + gd - 34840408L
        d -= (((gy + 100100 + (gm - 8) / 6).toLong() / 100) * 3) / 4 - 752L
        return d
    }

    /** Gregorian date of a Julian Day Number (jalaali algorithm). */
    private fun d2g(jdn: Long): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631L
        j += ((4 * jdn + 183187720L) / 146097L * 3) / 4 * 4 - 3908L
        val i = (j % 1461L) / 4 * 5 + 308
        val gd = ((i % 153L) / 5 + 1).toInt()
        val gm = ((i / 153L) % 12L + 1).toInt()
        val gy = (j / 1461L - 100100L + (8L - gm) / 6L).toInt()
        return Triple(gy, gm, gd)
    }

    /** Julian Day Number of 1 Farvardin of the Jalali year [jy]. */
    private fun farvardin1Jdn(jy: Int): Long {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march)
    }

    /** Julian Day Number of a Jalali date. */
    private fun j2d(jy: Int, jm: Int, jd: Int): Long =
        farvardin1Jdn(jy) + (jm - 1) * 31L - (jm / 7).toLong() * (jm - 7) + jd - 1

    /** Jalali date of a Julian Day Number (search-based, robust). */
    private fun d2j(jdn: Long): JalaliDate {
        val gy = d2g(jdn).first
        val approxJy = gy - 621
        var jy = approxJy
        // Locate the Jalali year whose 1 Farvardin precedes or equals jdn.
        while (farvardin1Jdn(jy) > jdn) jy--
        while (farvardin1Jdn(jy + 1) <= jdn) jy++
        val k = (jdn - farvardin1Jdn(jy)).toInt()
        return if (k <= 185) {
            JalaliDate(jy, 1 + k / 31, k % 31 + 1)
        } else {
            val k2 = k - 186
            JalaliDate(jy, 7 + k2 / 30, k2 % 30 + 1)
        }
    }
}
