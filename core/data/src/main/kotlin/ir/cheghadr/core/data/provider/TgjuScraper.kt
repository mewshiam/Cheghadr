package ir.cheghadr.core.data.provider

import ir.cheghadr.core.common.JalaliCalendar
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.RawQuote
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure HTML parsing for tgju.org pages — no network I/O, fully unit-testable
 * (fixtures captured from the live site on 2026-10-14; see TgjuScraperTest).
 *
 * Page anatomy relied upon:
 *  - Section pages (`/currency`, `/gold-chart`, `/coin`) render one `<table>`
 *    per market group. Every row carries
 *      `<tr data-market-row="{symbol}" data-price="2,609,000">` with cells
 *      [price, change, dailyLow, dailyHigh, updateTime] and the change cell
 *      contains `<span class="high|low">(P%) AMOUNT</span>` (class = direction).
 *  - Every page embeds a site-wide live sidebar whose items are
 *      `<li id="l-{symbol}" class="high|low">… <span class="info-price">…`
 *      `<span class="info-change">(P%)</span>`. It is the only server-rendered
 *      source for symbols absent from section tables (gc30, oil_brent).
 *  - Profile pages (`/profile/{symbol}`) render a daily OHLC table with rows
 *      `[JalaliDate, open, low, high, close, changePercent, changeAmount]`
 *      (headers: تاریخ، بازگشایی، کمترین، بیشترین، پایانی، درصد تغییر، میزان تغییر)
 *    covering roughly the last 25 calendar days.
 *
 * All numbers may appear in ASCII or Persian digits, grouped with ',' or '٬';
 * some rows omit separators entirely ("2550000000").
 */
object TgjuScraper {

    private val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

    private val CHANGE_REGEX = Regex("""\((-?\d+(?:\.\d+)?)%\)\s*(-?\d+(?:\.\d+)?)""")
    private val PERCENT_REGEX = Regex("""\((-?\d+(?:\.\d+)?)%\)""")
    private val TIME_REGEX = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""")
    private val JALALI_DATE_REGEX = Regex("""^(\d{4})/(\d{1,2})/(\d{1,2})$""")

    // ---------------------------------------------------------------- numbers

    /** Persian (۰-۹) and Arabic-Indic (٠-٩) digits → ASCII. */
    fun normalizeDigits(text: String): String {
        if (text.none { it in '\u06F0'..'\u06F9' || it in '\u0660'..'\u0669' }) return text
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when {
                    c in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    c in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /** "2,609,000" / "۲,۶۰۹,۰۰۰" / "58٬000" / "2550000000" / "-1.5" → BigDecimal. */
    fun parseTgjuNumber(text: String): BigDecimal? {
        val cleaned = normalizeDigits(text)
            .replace(",", "")
            .replace("٬", "") // Persian thousands separator
            .replace("،", "") // Persian comma
            .replace('−', '-') // Unicode minus
            .replace('–', '-') // En dash
            .trim()
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == ".") return null
        return runCatching { BigDecimal(cleaned) }.getOrNull()
    }

    // ------------------------------------------------------- section tables

    /**
     * Parses all market rows of a section page into (tgjuSymbol → RawQuote).
     * Rows without a usable price are skipped; unknown symbols are filtered
     * later by the provider via the asset catalog.
     */
    fun parseMarketRows(html: String, now: Long = System.currentTimeMillis()): Map<String, RawQuote> {
        val doc = Jsoup.parse(html)
        val out = mutableMapOf<String, RawQuote>()
        for (tr in doc.select("tr[data-market-row]")) {
            val symbol = tr.attr("data-market-row").trim()
            if (symbol.isEmpty()) continue
            val tds = tr.select("td")
            val price = parseTgjuNumber(tr.attr("data-price"))
                ?: tds.firstOrNull()?.let { parseTgjuNumber(it.text()) }
            if (price == null || price.signum() <= 0) continue
            val (changePercent, changeAbs) = parseChangeCell(tr.selectFirst("td span.high, td span.low"))
            out[symbol] = RawQuote(
                price = price,
                changePercent = changePercent,
                changeAbs = changeAbs,
                low24h = tds.getOrNull(2)?.let { parseTgjuNumber(it.text()) }?.takeIf { it.signum() > 0 },
                high24h = tds.getOrNull(3)?.let { parseTgjuNumber(it.text()) }?.takeIf { it.signum() > 0 },
                priceTime = tds.getOrNull(4)?.let { parseTgjuDateTime(it.text(), now) },
            )
        }
        return out
    }

    /** "(1.24%) 31,900" + span class high/low → signed (percent, absolute). */
    private fun parseChangeCell(span: Element?): Pair<BigDecimal?, BigDecimal?> {
        if (span == null) return null to null
        val text = normalizeDigits(span.text()).replace(",", "")
        val match = CHANGE_REGEX.find(text) ?: return null to null
        val percent = runCatching { BigDecimal(match.groupValues[1]) }.getOrNull()
        val absolute = runCatching { BigDecimal(match.groupValues[2]) }.getOrNull()
        val negative = text.contains('-') ||
            (span.classNames().contains("low") && !text.contains('+'))
        return if (negative) {
            (percent?.negate() to absolute?.negate())
        } else {
            (percent to absolute)
        }
    }

    // ---------------------------------------------------------------- sidebar

    /**
     * Parses the site-wide live sidebar (`li#l-{symbol}`) for the requested
     * symbols. Used for markets that have no section-table row (gc30, oil_brent)
     * and for the current price shown on profile pages.
     */
    fun parseSidebar(html: String, wanted: Set<String>): Map<String, RawQuote> {
        if (wanted.isEmpty()) return emptyMap()
        val doc = Jsoup.parse(html)
        val out = mutableMapOf<String, RawQuote>()
        for (li in doc.select("li[id^=l-]")) {
            val symbol = li.id().removePrefix("l-").trim()
            if (symbol !in wanted) continue
            val price = parseTgjuNumber(li.selectFirst(".info-price")?.text() ?: continue) ?: continue
            if (price.signum() <= 0) continue
            val down = li.classNames().contains("low")
            out[symbol] = RawQuote(
                price = price,
                changePercent = parseSignedPercent(li.selectFirst(".info-change")?.text(), down),
            )
        }
        return out
    }

    /** "(0.73%)" + direction → signed percentage. */
    private fun parseSignedPercent(text: String?, down: Boolean): BigDecimal? {
        if (text == null) return null
        val cleaned = normalizeDigits(text).replace(",", "")
        val match = PERCENT_REGEX.find(cleaned) ?: return null
        val value = runCatching { BigDecimal(match.groupValues[1]) }.getOrNull() ?: return null
        val negative = cleaned.contains('-') || (down && !cleaned.contains('+'))
        return if (negative) value.negate() else value
    }

    // ---------------------------------------------------------------- history

    /**
     * Parses a profile page's daily OHLC table into history points (close per
     * day) and appends the live sidebar price as the current point, since the
     * table only covers up to yesterday.
     */
    fun parseHistory(html: String, symbol: String, now: Long = System.currentTimeMillis()): List<HistoryPoint> {
        val doc = Jsoup.parse(html)
        val byDay = LinkedHashMap<Long, BigDecimal>()
        for (table in doc.select("table")) {
            for (tr in table.select("tr")) {
                val tds = tr.select("td")
                if (tds.size < 5) continue
                val date = parseJalaliDate(tds[0].text()) ?: continue
                val close = parseTgjuNumber(tds[4].text()) ?: continue
                if (close.signum() <= 0) continue
                byDay[date.atStartOfDay(TEHRAN).toInstant().toEpochMilli()] = close
            }
        }
        // Live point from the sidebar (history table lags one day behind).
        doc.selectFirst("li[id=\"l-$symbol\"]")?.selectFirst(".info-price")?.text()
            ?.let { parseTgjuNumber(it) }
            ?.takeIf { it.signum() > 0 }
            ?.let { byDay[alignedNow(now)] = it }
        return byDay.entries
            .sortedBy { it.key }
            .map { HistoryPoint(it.key, it.value) }
    }

    // ------------------------------------------------------------------ dates

    /** "1405/07/21" (ASCII or Persian digits) → Gregorian LocalDate. */
    internal fun parseJalaliDate(text: String): LocalDate? {
        val cleaned = normalizeDigits(text).trim()
        val match = JALALI_DATE_REGEX.find(cleaned) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        if (year !in 1300..1500 || month !in 1..12 || day !in 1..31) return null
        return runCatching {
            JalaliCalendar.toGregorian(JalaliCalendar.JalaliDate(year, month, day))
        }.getOrNull()
    }

    /**
     * Row time cells are either a time-of-day ("۱۲:۳۴:۵۸", Persian digits,
     * Tehran zone) or, for quiet markets, a bare Jalali day-month ("۱۶ مهر").
     * Returns epoch millis or null when the cell cannot be interpreted.
     */
    internal fun parseTgjuDateTime(text: String, now: Long): Long? {
        val cleaned = normalizeDigits(text).trim()
        if (cleaned.isEmpty()) return null

        TIME_REGEX.find(cleaned)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            val second = match.groupValues[3].toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
            var zoned = todayInTehran(now).atTime(hour, minute, second).atZone(TEHRAN)
            // A time later than "now" belongs to the previous session.
            if (zoned.toInstant().toEpochMilli() > now + FUTURE_TOLERANCE_MS) {
                zoned = zoned.minusDays(1)
            }
            return zoned.toInstant().toEpochMilli()
        }

        val parts = cleaned.split(' ', '\u00A0').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size == 2) {
            val day = parts[0].toIntOrNull() ?: return null
            val month = JalaliCalendar.monthNamesFa.indexOf(parts[1]).takeIf { it >= 0 } ?: return null
            if (day !in 1..31) return null
            val today = todayInTehran(now)
            val todayJalali = JalaliCalendar.toJalali(today)
            var date = runCatching {
                JalaliCalendar.toGregorian(JalaliCalendar.JalaliDate(todayJalali.year, month + 1, day))
            }.getOrNull() ?: return null
            if (date.isAfter(today)) {
                date = runCatching {
                    JalaliCalendar.toGregorian(JalaliCalendar.JalaliDate(todayJalali.year - 1, month + 1, day))
                }.getOrNull() ?: return null
            }
            return date.atTime(12, 0).atZone(TEHRAN).toInstant().toEpochMilli()
        }
        return null
    }

    private fun todayInTehran(now: Long): LocalDate =
        Instant.ofEpochMilli(now).atZone(TEHRAN).toLocalDate()

    private fun alignedNow(now: Long): Long = now / 60_000L * 60_000L

    private const val FUTURE_TOLERANCE_MS = 5L * 60_000L
}
