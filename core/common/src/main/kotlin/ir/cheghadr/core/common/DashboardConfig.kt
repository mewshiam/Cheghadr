package ir.cheghadr.core.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Identifiable dashboard sections (blueprint §7). The order of
 * [DashboardConfig.sections] defines the rendering order on the Home screen.
 */
enum class DashboardSection {
    FEATURED,
    QUICK_CONVERTER,
    WATCHLIST_PREVIEW,
    MARKET_OVERVIEW,
}

/** Row density of the featured grid. */
enum class DashboardDensity { COMFORTABLE, COMPACT }

/**
 * User-customizable dashboard layout. Everything persists locally (DataStore)
 * and no account is required. An empty [featuredAssetIds] means "automatic"
 * (watchlist first, then sensible defaults).
 */
@Serializable
data class DashboardConfig(
    /** Ordered section states; hidden sections stay in the list with visible = false. */
    val sections: List<SectionState> = DEFAULT_SECTIONS,
    /** Explicitly chosen featured assets, in order. Empty → automatic selection. */
    val featuredAssetIds: List<String> = emptyList(),
    val density: DashboardDensity = DashboardDensity.COMFORTABLE,
    val showMiniCharts: Boolean = true,
    val showChanges: Boolean = true,
) {
    @Serializable
    data class SectionState(
        val id: DashboardSection,
        val visible: Boolean = true,
    )

    val featuredIsManual: Boolean get() = featuredAssetIds.isNotEmpty()

    fun isVisible(section: DashboardSection): Boolean =
        sections.firstOrNull { it.id == section }?.visible ?: true

    fun visibleSections(): List<DashboardSection> =
        sections.filter { it.visible }.map { it.id }

    companion object {
        /** The default layout: featured, quick converter, watchlist, (overview hidden). */
        val DEFAULT_SECTIONS = listOf(
            SectionState(DashboardSection.FEATURED, visible = true),
            SectionState(DashboardSection.QUICK_CONVERTER, visible = true),
            SectionState(DashboardSection.WATCHLIST_PREVIEW, visible = true),
            SectionState(DashboardSection.MARKET_OVERVIEW, visible = false),
        )

        val DEFAULT = DashboardConfig()

        /** Hard ceiling for manual featured picks — keeps the dashboard usable. */
        const val MAX_FEATURED = 8

        private fun defaultPosition(section: DashboardSection): Int =
            DEFAULT_SECTIONS.indexOfFirst { it.id == section }

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(config: DashboardConfig): String =
            json.encodeToString(serializer(), config)

        /** Returns null when the payload is absent or not decodable. */
        fun decodeOrNull(raw: String?): DashboardConfig? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
        }

        /**
         * Repairs a decoded (or partial) config so the renderer can trust it:
         *  - dedupes repeated sections and restores missing ones at their
         *    default relative positions (keeping the user's order otherwise),
         *  - dedupes featured ids and caps their count,
         *  - null input yields the default layout.
         */
        fun normalize(config: DashboardConfig?): DashboardConfig {
            if (config == null) return DEFAULT

            val seen = mutableSetOf<DashboardSection>()
            val ordered = config.sections.filter { seen.add(it.id) }.toMutableList()
            DEFAULT_SECTIONS.map { it.id }.forEach { def ->
                if (def !in seen) {
                    val defPos = defaultPosition(def)
                    val insertAt = ordered.indexOfLast { existing ->
                        defaultPosition(existing.id) < defPos
                    } + 1
                    ordered.add(insertAt.coerceIn(0, ordered.size), SectionState(def))
                }
            }

            return config.copy(
                sections = ordered,
                featuredAssetIds = config.featuredAssetIds.distinct().take(MAX_FEATURED),
            )
        }
    }
}
