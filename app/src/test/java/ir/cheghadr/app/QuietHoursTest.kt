package ir.cheghadr.app

import ir.cheghadr.app.sync.AlertWorker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class QuietHoursTest {

    private fun timeAt(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }.timeInMillis

    @Test
    fun `overnight window covers late evening and early morning`() {
        // 22:00 → 08:00
        val start = 22 * 60
        val end = 8 * 60
        assertTrue(AlertWorker.isQuietHours(start, end, timeAt(23, 30)))
        assertTrue(AlertWorker.isQuietHours(start, end, timeAt(2, 15)))
        assertTrue(AlertWorker.isQuietHours(start, end, timeAt(7, 59)))
        assertFalse(AlertWorker.isQuietHours(start, end, timeAt(8, 0)))
        assertFalse(AlertWorker.isQuietHours(start, end, timeAt(12, 0)))
        assertFalse(AlertWorker.isQuietHours(start, end, timeAt(21, 59)))
        assertTrue(AlertWorker.isQuietHours(start, end, timeAt(22, 0)))
    }

    @Test
    fun `daytime window`() {
        // 12:00 → 14:00
        val start = 12 * 60
        val end = 14 * 60
        assertTrue(AlertWorker.isQuietHours(start, end, timeAt(13, 0)))
        assertFalse(AlertWorker.isQuietHours(start, end, timeAt(11, 59)))
        assertFalse(AlertWorker.isQuietHours(start, end, timeAt(14, 0)))
    }

    @Test
    fun `empty window is never quiet`() {
        assertFalse(AlertWorker.isQuietHours(0, 0, timeAt(3, 0)))
        assertFalse(AlertWorker.isQuietHours(600, 600, timeAt(10, 0)))
    }
}
