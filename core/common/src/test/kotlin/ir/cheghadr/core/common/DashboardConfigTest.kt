package ir.cheghadr.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round-trip and repair semantics of the persisted dashboard layout. */
class DashboardConfigTest {

    @Test
    fun `encode decode round trip preserves everything`() {
        val config = DashboardConfig(
            sections = listOf(
                DashboardConfig.SectionState(DashboardSection.WATCHLIST_PREVIEW, visible = true),
                DashboardConfig.SectionState(DashboardSection.FEATURED, visible = true),
                DashboardConfig.SectionState(DashboardSection.QUICK_CONVERTER, visible = false),
            ),
            featuredAssetIds = listOf("crypto:BTC", "gold:ounce"),
            density = DashboardDensity.COMPACT,
            showMiniCharts = false,
            showChanges = false,
        )
        val decoded = DashboardConfig.decodeOrNull(DashboardConfig.encode(config))
        assertEquals(config, decoded)
    }

    @Test
    fun `decode of null blank and garbage yields null`() {
        assertNull(DashboardConfig.decodeOrNull(null))
        assertNull(DashboardConfig.decodeOrNull(""))
        assertNull(DashboardConfig.decodeOrNull("   "))
        assertNull(DashboardConfig.decodeOrNull("not-json{"))
        assertNull(DashboardConfig.decodeOrNull("{\"sections\": 42}"))
    }

    @Test
    fun `normalize of null yields the default layout`() {
        val normalized = DashboardConfig.normalize(null)
        assertEquals(DashboardConfig.DEFAULT, normalized)
        assertEquals(
            listOf(
                DashboardSection.FEATURED,
                DashboardSection.QUICK_CONVERTER,
                DashboardSection.WATCHLIST_PREVIEW,
            ),
            normalized.visibleSections(),
        )
        assertFalse(normalized.isVisible(DashboardSection.MARKET_OVERVIEW))
    }

    @Test
    fun `normalize dedupes repeated sections`() {
        val config = DashboardConfig(
            sections = listOf(
                DashboardConfig.SectionState(DashboardSection.FEATURED, visible = true),
                DashboardConfig.SectionState(DashboardSection.FEATURED, visible = false),
            ),
        )
        val normalized = DashboardConfig.normalize(config)
        assertEquals(1, normalized.sections.count { it.id == DashboardSection.FEATURED })
        // First occurrence wins: FEATURED stays visible.
        assertTrue(normalized.isVisible(DashboardSection.FEATURED))
        // The other sections are restored.
        assertTrue(normalized.isVisible(DashboardSection.WATCHLIST_PREVIEW))
    }

    @Test
    fun `normalize restores missing sections at default relative positions`() {
        // User kept only the watchlist (moved to front).
        val config = DashboardConfig(
            sections = listOf(
                DashboardConfig.SectionState(DashboardSection.WATCHLIST_PREVIEW, visible = true),
                DashboardConfig.SectionState(DashboardSection.FEATURED, visible = true),
            ),
        )
        val normalized = DashboardConfig.normalize(config)
        // User order for known sections is preserved: watchlist before featured.
        assertEquals(
            listOf(
                DashboardSection.WATCHLIST_PREVIEW,
                DashboardSection.FEATURED,
                DashboardSection.QUICK_CONVERTER,
                DashboardSection.MARKET_OVERVIEW,
            ),
            normalized.sections.map { it.id },
        )
    }

    @Test
    fun `normalize dedupes and caps featured ids`() {
        val ids = listOf("a", "a", "b") + ('c'..'z').map { it.toString() }
        val normalized = DashboardConfig.normalize(DashboardConfig(featuredAssetIds = ids))
        assertEquals(DashboardConfig.MAX_FEATURED, normalized.featuredAssetIds.size)
        assertEquals(ids.distinct().take(DashboardConfig.MAX_FEATURED), normalized.featuredAssetIds)
        assertTrue(normalized.featuredIsManual)
    }

    @Test
    fun `hidden sections survive round trip`() {
        val config = DashboardConfig.DEFAULT.copy(
            sections = DashboardConfig.DEFAULT_SECTIONS.map {
                if (it.id == DashboardSection.QUICK_CONVERTER) it.copy(visible = false) else it
            },
        )
        val decoded = DashboardConfig.decodeOrNull(DashboardConfig.encode(config))
        assertNotNull(decoded)
        assertEquals(config, decoded)
        assertFalse(config.isVisible(DashboardSection.QUICK_CONVERTER))
    }
}
