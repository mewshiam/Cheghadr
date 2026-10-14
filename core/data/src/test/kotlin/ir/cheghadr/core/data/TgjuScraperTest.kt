package ir.cheghadr.core.data

import ir.cheghadr.core.data.provider.TgjuScraper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * tgju.org scraper tests. Fixtures are trimmed snippets captured from the
 * live site on 2026-10-14 (docs/DATA_SOURCES.md): /currency, /gold-chart,
 * /coin section tables, the site-wide live sidebar and /profile/geram18's
 * daily OHLC table.
 */
class TgjuScraperTest {

    private val tehran = ZoneId.of("Asia/Tehran")

    // ------------------------------------------------------------- numbers

    @Test
    fun `numbers with ascii persian and arabic digits`() {
        assertEquals(0, BigDecimal("2609000").compareTo(TgjuScraper.parseTgjuNumber("2,609,000")!!))
        assertEquals(0, BigDecimal("2609000").compareTo(TgjuScraper.parseTgjuNumber("۲,۶۰۹,۰۰۰")!!))
        assertEquals(0, BigDecimal("2609000").compareTo(TgjuScraper.parseTgjuNumber("٢,٦٠٩,٠٠٠")!!))
        assertEquals(0, BigDecimal("58000").compareTo(TgjuScraper.parseTgjuNumber("58٬000")!!))
        assertEquals(0, BigDecimal("2550000000").compareTo(TgjuScraper.parseTgjuNumber("2550000000")!!))
        assertEquals(0, BigDecimal("-1.5").compareTo(TgjuScraper.parseTgjuNumber("-1.5")!!))
        assertEquals(0, BigDecimal("105.679").compareTo(TgjuScraper.parseTgjuNumber("105.679")!!))
        assertNull(TgjuScraper.parseTgjuNumber(""))
        assertNull(TgjuScraper.parseTgjuNumber("abc"))
        assertNull(TgjuScraper.parseTgjuNumber("-"))
    }

    // -------------------------------------------------------- section rows

    private val sectionPage = """
        <html><body><table class="market-table">
        <thead><tr><th>عنوان</th><th>قیمت زنده</th><th>تغییر</th><th>کمترین</th><th>بیشترین</th><th>زمان</th><th></th></tr></thead>
        <tbody>
        <tr data-market-nameslug="price_dollar_rl" data-market-row="price_dollar_rl"
            data-title="<div class='tooltip-row'><span class='tooltip-row-txt'>2609000 در ۱۲:۲۶:۲۳ </span></div>"
            class="pointer" onclick="window.location='profile/price_dollar_rl'" data-price="2,609,000">
            <th><span class="mini-flag flag-us"></span>دلار</th>
            <td class="nf">2,609,000</td>
            <td class="nf"><span class="high">(1.24%) 31,900</span></td>
            <td>2,599,000</td>
            <td>2,619,000</td>
            <td>۱۲:۳۴:۵۸</td>
            <td class="chart-td tg-1"><a class="chart-icon" href="profile/price_dollar_rl"></a></td>
        </tr>
        <tr data-market-row="price_eur" class="pointer" data-price="2,929,900">
            <th>یورو</th>
            <td class="nf">2,929,900</td>
            <td class="nf"><span class="low">(0.42%) 12,400</span></td>
            <td>2,912,700</td>
            <td>2,932,000</td>
            <td>۱۲:۳۴:۵۷</td>
            <td class="chart-td"></td>
        </tr>
        <tr data-market-row="sekee_down" class="pointer" data-price="2550000000">
            <th>فروش سکه</th>
            <td class="nf">2550000000</td>
            <td class="nf"><span class="high">(2%) 50000000</span></td>
            <td>2550000000</td>
            <td>2550000000</td>
            <td>۱۱:۰۴:۵۵</td>
            <td class="chart-td"></td>
        </tr>
        <tr data-market-row="geram18" class="pointer">
            <th>طلای ۱۸ عیار</th>
            <td class="nf">۲,۶۰,۱۱۱,۰۰۰</td>
            <td class="nf"><span class="high">(1.05%) 2,700,000</span></td>
            <td>257,392,000</td>
            <td>260,178,000</td>
            <td>۱۲:۲۹:۵۶</td>
            <td class="chart-td"></td>
        </tr>
        <tr data-market-row="price_iqd" class="pointer" data-price="0">
            <th>دینار عراق</th>
            <td class="nf">0</td>
            <td class="nf"><span class="low">(0%) 0</span></td>
            <td>1,718</td>
            <td>1,769</td>
            <td>۱۶ مهر</td>
            <td class="chart-td"></td>
        </tr>
        </tbody></table></body></html>
    """.trimIndent()

    @Test
    fun `market rows parse price change low high and time`() {
        val now = ZonedDateTime.of(2026, 10, 14, 17, 0, 0, 0, tehran).toInstant().toEpochMilli()
        val rows = TgjuScraper.parseMarketRows(sectionPage, now)
        assertEquals(4, rows.size) // the zero-price row is dropped

        val dollar = rows["price_dollar_rl"]!!
        assertEquals(0, BigDecimal("2609000").compareTo(dollar.price!!))
        assertEquals(0, BigDecimal("1.24").compareTo(dollar.changePercent!!))
        assertEquals(0, BigDecimal("31900").compareTo(dollar.changeAbs!!))
        assertEquals(0, BigDecimal("2599000").compareTo(dollar.low24h!!))
        assertEquals(0, BigDecimal("2619000").compareTo(dollar.high24h!!))
        val expectedTime = ZonedDateTime.of(2026, 10, 14, 12, 34, 58, 0, tehran).toInstant().toEpochMilli()
        assertEquals(expectedTime, dollar.priceTime)
    }

    @Test
    fun `low class yields negative change`() {
        val now = ZonedDateTime.of(2026, 10, 14, 17, 0, 0, 0, tehran).toInstant().toEpochMilli()
        val eur = TgjuScraper.parseMarketRows(sectionPage, now)["price_eur"]!!
        assertEquals(0, BigDecimal("-0.42").compareTo(eur.changePercent!!))
        assertEquals(0, BigDecimal("-12400").compareTo(eur.changeAbs!!))
    }

    @Test
    fun `data price without separators and td fallback parse`() {
        val now = ZonedDateTime.of(2026, 10, 14, 17, 0, 0, 0, tehran).toInstant().toEpochMilli()
        val rows = TgjuScraper.parseMarketRows(sectionPage, now)
        assertEquals(0, BigDecimal("2550000000").compareTo(rows["sekee_down"]!!.price!!))
        // geram18 has no data-price attribute → first td (Persian digits) is used.
        assertEquals(0, BigDecimal("260111000").compareTo(rows["geram18"]!!.price!!))
    }

    @Test
    fun `stale jalali day-month time cell parses`() {
        val now = ZonedDateTime.of(2026, 10, 14, 17, 0, 0, 0, tehran).toInstant().toEpochMilli()
        // 1405/07/16 = 2026-10-08, rendered at 12:00 Tehran.
        val stale = TgjuScraper.parseTgjuDateTime("۱۶ مهر", now)!!
        val expected = ZonedDateTime.of(2026, 10, 8, 12, 0, 0, 0, tehran).toInstant().toEpochMilli()
        assertEquals(expected, stale)
    }

    @Test
    fun `future time-of-day rolls back one day`() {
        // Device "now" is 11:30 Tehran; a 12:34 update must belong to yesterday.
        val now = ZonedDateTime.of(2026, 10, 14, 11, 30, 0, 0, tehran).toInstant().toEpochMilli()
        val parsed = TgjuScraper.parseTgjuDateTime("۱۲:۳۴:۵۸", now)!!
        val expected = ZonedDateTime.of(2026, 10, 13, 12, 34, 58, 0, tehran).toInstant().toEpochMilli()
        assertEquals(expected, parsed)
    }

    @Test
    fun `jalali history dates convert to gregorian`() {
        assertEquals(LocalDate.of(2026, 10, 13), TgjuScraper.parseJalaliDate("1405/07/21"))
        assertEquals(LocalDate.of(2026, 10, 8), TgjuScraper.parseJalaliDate("۱۴۰۵/۷/۱۶"))
        assertNull(TgjuScraper.parseJalaliDate("12:34"))
        assertNull(TgjuScraper.parseJalaliDate("9999/99/99"))
    }

    // ---------------------------------------------------------------- sidebar

    private val sidebarHtml = """
        <html><body><ul class="market-info">
        <li id="l-gc30" class="low">
            <strong>بورس</strong>
            <span class="info-value"><span class="info-price">8,428,502</span> <span class="info-change">(0.73%)</span></span>
        </li>
        <li id="l-ons" class="low">
            <strong>انس طلا</strong>
            <span class="info-value"><span class="info-price">4,144.47</span> <span class="info-change">(0.35%)</span></span>
        </li>
        <li id="l-price_dollar_rl" class="high">
            <strong>دلار</strong>
            <span class="info-value"><span class="info-price">2,609,000</span> <span class="info-change">(1.24%)</span></span>
        </li>
        <li id="l-oil_brent" class="low">
            <strong>نفت برنت</strong>
            <span class="info-value"><span class="info-price">105.679</span> <span class="info-change">(0.83%)</span></span>
        </li>
        </ul></body></html>
    """.trimIndent()

    @Test
    fun `sidebar yields only wanted symbols with signed percent`() {
        val sidebar = TgjuScraper.parseSidebar(
            sidebarHtml,
            setOf("gc30", "oil_brent"),
        )
        assertEquals(2, sidebar.size)
        val bourse = sidebar["gc30"]!!
        assertEquals(0, BigDecimal("8428502").compareTo(bourse.price!!))
        assertEquals(0, BigDecimal("-0.73").compareTo(bourse.changePercent!!))
        assertNull(bourse.priceTime)
        val brent = sidebar["oil_brent"]!!
        assertEquals(0, BigDecimal("105.679").compareTo(brent.price!!))
        assertEquals(0, BigDecimal("-0.83").compareTo(brent.changePercent!!))
    }

    @Test
    fun `sidebar ignores items without wanted symbols`() {
        assertTrue(TgjuScraper.parseSidebar(sidebarHtml, emptySet()).isEmpty())
        assertEquals(1, TgjuScraper.parseSidebar(sidebarHtml, setOf("ons")).size)
    }

    // ---------------------------------------------------------------- history

    private val profilePage = """
        <html><body>
        <ul class="market-info">
        <li id="l-gc30" class="low"><strong>بورس</strong>
            <span class="info-value"><span class="info-price">8,428,502</span> <span class="info-change">(0.73%)</span></span></li>
        <li id="l-geram18" class="high"><strong>طلا ۱۸</strong>
            <span class="info-value"><span class="info-price">260,111,000</span> <span class="info-change">(1.05%)</span></span></li>
        </ul>
        <div class="tables-default normal">
        <table class="table table-hover text-center">
        <thead><tr>
            <th>تاریخ</th><th>بازگشایی</th><th>کمترین</th><th>بیشترین</th><th>پایانی</th><th>درصد تغییر</th><th>میزان تغییر</th>
        </tr></thead>
        <tbody>
        <tr><td>1405/07/21</td><td>259,617,000</td><td>255,725,000</td><td>259,617,000</td><td>257,396,000</td><td>0.86%</td><td>2223000</td></tr>
        <tr><td>1405/07/20</td><td>262,992,000</td><td>259,511,000</td><td>263,066,000</td><td>259,619,000</td><td>1.29%</td><td>3359000</td></tr>
        <tr><td>1405/07/19</td><td>264,959,000</td><td>262,466,000</td><td>264,959,000</td><td>262,978,000</td><td>0.75%</td><td>1983000</td></tr>
        </tbody></table>
        <table class="market-table">
        <thead><tr><th>بازار</th><th>قیمت</th></tr></thead>
        <tbody><tr><td>سکه امامی</td><td>2,648,100,000</td></tr></tbody>
        </table>
        </div></body></html>
    """.trimIndent()

    @Test
    fun `history parses ohlc closes plus live sidebar point`() {
        // "now" sits after the table's latest row (1405/07/21 = 2026-10-13),
        // as in production where the OHLC table lags the live sidebar by a day.
        val now = ZonedDateTime.of(2026, 10, 14, 17, 0, 0, 0, tehran).toInstant().toEpochMilli()
        val points = TgjuScraper.parseHistory(profilePage, "geram18", now)

        // 3 daily closes + today's live point.
        assertEquals(4, points.size)
        // Sorted ascending; daily closes at Tehran midnight.
        assertEquals(
            LocalDate.of(2026, 10, 11).atStartOfDay(tehran).toInstant().toEpochMilli(),
            points[0].timestamp,
        )
        assertEquals(0, BigDecimal("262978000").compareTo(points[0].price))
        assertEquals(0, BigDecimal("259619000").compareTo(points[1].price))
        assertEquals(0, BigDecimal("257396000").compareTo(points[2].price))
        // Live point: sidebar price at the aligned fetch time.
        assertEquals(0, BigDecimal("260111000").compareTo(points[3].price))
        assertEquals(now / 60_000L * 60_000L, points[3].timestamp)
    }

    @Test
    fun `history without a sidebar yields table rows only`() {
        val points = TgjuScraper.parseHistory(
            profilePage,
            "price_dollar_rl",
            ZonedDateTime.of(2026, 10, 14, 17, 0, 0, 0, tehran).toInstant().toEpochMilli(),
        )
        assertEquals(3, points.size)
        assertTrue(points[0].timestamp < points[2].timestamp)
    }

    @Test
    fun `malformed page yields empty history`() {
        assertTrue(TgjuScraper.parseHistory("<html></html>", "geram18").isEmpty())
        assertTrue(TgjuScraper.parseMarketRows("not html").isEmpty())
    }
}
