package ir.cheghadr.core.common

import java.time.Instant
import java.time.ZoneId

/**
 * Timestamp rendering with Jalali dates + Persian digits for fa, and
 * Gregorian dates for en. Pure and unit-testable.
 */
object TimeFormat {

    /** e.g. «۱۴۰۳/۰۷/۲۲ ۱۴:۰۵» (fa) or "2024-10-14 09:35" (en). */
    fun formatDateTime(epochMillis: Long, persian: Boolean): String {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return if (persian) {
            val j = JalaliCalendar.toJalali(dateTime.toLocalDate())
            MoneyFormatter.localizeDigitsAndSeparators(
                "%04d/%02d/%02d %02d:%02d".format(
                    j.year, j.month, j.day, dateTime.hour, dateTime.minute,
                ),
                DigitStyle.PERSIAN,
            )
        } else {
            "%04d-%02d-%02d %02d:%02d".format(
                dateTime.year, dateTime.monthValue, dateTime.dayOfMonth, dateTime.hour, dateTime.minute,
            )
        }
    }

    /** Short date only. */
    fun formatDate(epochMillis: Long, persian: Boolean): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return if (persian) {
            val j = JalaliCalendar.toJalali(date)
            MoneyFormatter.localizeDigitsAndSeparators(
                "%04d/%02d/%02d".format(j.year, j.month, j.day),
                DigitStyle.PERSIAN,
            )
        } else {
            "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
        }
    }

    /** Long human form, e.g. «۲۲ مهر ۱۴۰۳». */
    fun formatLongDate(epochMillis: Long, persian: Boolean): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return if (persian) {
            val j = JalaliCalendar.toJalali(date)
            MoneyFormatter.localizeDigitsAndSeparators(
                JalaliCalendar.formatLongFa(j),
                DigitStyle.PERSIAN,
            )
        } else {
            "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
        }
    }
}
