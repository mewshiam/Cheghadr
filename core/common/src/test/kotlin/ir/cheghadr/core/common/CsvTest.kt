package ir.cheghadr.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** CSV escaping/building rules for exports (blueprint §15). */
class CsvTest {

    @Test
    fun `plain fields pass through unchanged`() {
        assertEquals("BTC,123.45", Csv.row(listOf("BTC", "123.45")))
    }

    @Test
    fun `commas force quoting`() {
        assertEquals("\"a,b\"", Csv.escape("a,b"))
    }

    @Test
    fun `quotes are doubled inside quoted fields`() {
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
    }

    @Test
    fun `newlines force quoting`() {
        assertEquals("\"line1\nline2\"", Csv.escape("line1\nline2"))
        assertEquals("\"line1\r\nline2\"", Csv.escape("line1\r\nline2"))
    }

    @Test
    fun `persian text is preserved`() {
        assertEquals("دلار آزاد", Csv.escape("دلار آزاد"))
        // The Arabic comma (U+060C) is not a CSV separator — no quoting needed.
        assertEquals("دلار، آزاد", Csv.escape("دلار، آزاد"))
        // …but an ASCII comma inside Persian text still forces quoting.
        assertEquals("\"دلار, آزاد\"", Csv.escape("دلار, آزاد"))
    }

    @Test
    fun `empty field stays empty`() {
        assertEquals("a,,c", Csv.row(listOf("a", "", "c")))
    }

    @Test
    fun `build joins header and rows with crlf`() {
        val csv = Csv.build(
            header = listOf("id", "name"),
            rows = listOf(listOf("1", "one"), listOf("2", "two,too")),
        )
        assertEquals("id,name\r\n1,one\r\n2,\"two,too\"", csv)
    }
}
