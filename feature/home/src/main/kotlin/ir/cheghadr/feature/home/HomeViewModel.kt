package ir.cheghadr.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.DashboardConfig
import ir.cheghadr.core.common.DashboardDensity
import ir.cheghadr.core.common.DashboardSection
import ir.cheghadr.core.common.DisplaySettings
import ir.cheghadr.core.common.ThemeMode
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.AssetWithQuote
import ir.cheghadr.core.data.repo.FavoritesRepository
import ir.cheghadr.core.data.repo.MarketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val settings: DisplaySettings = DisplaySettings(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = false,
    /** Dashboard layout (blueprint §7). */
    val dashboard: DashboardConfig = DashboardConfig.DEFAULT,
    /** Visible sections in render order. */
    val sections: List<DashboardSection> = DashboardConfig.DEFAULT.visibleSections(),
    val featured: List<AssetWithQuote> = emptyList(),
    /** Mini-chart series per featured asset (device's own observations). */
    val sparklines: Map<String, List<Double>> = emptyMap(),
    /** Biggest 24h gainers and losers (market overview section). */
    val topGainers: List<AssetWithQuote> = emptyList(),
    val topLosers: List<AssetWithQuote> = emptyList(),
    val watchlistPreview: List<AssetWithQuote> = emptyList(),
    /** Official rial-quoted USD, for the quick-converter card. */
    val usdOfficial: AssetWithQuote? = null,
    val lastSyncAt: Long? = null,
    val refreshing: Boolean = false,
    val hasData: Boolean = false,
) {
    val density: DashboardDensity get() = dashboard.density
    val compact: Boolean get() = dashboard.density == DashboardDensity.COMPACT
}

/** Raw inputs combined into the public UI state (keeps combine() under 5 flows). */
private data class HomeInputs(
    val available: List<AssetWithQuote>,
    val favorites: List<AssetWithQuote>,
    val lastSyncAt: Long?,
    val settings: ir.cheghadr.core.data.preferences.UserSettings,
    val dashboard: DashboardConfig,
)

/**
 * Home dashboard state. Renders cached data immediately and refreshes on
 * entry; the featured set follows the user's dashboard configuration and
 * falls back to a sensible default when unconfigured.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val sparklines = MutableStateFlow<Map<String, List<Double>>>(emptyMap())

    private val inputs = combine(
        marketRepository.observeAvailable(),
        favoritesRepository.observeFavorites(),
        marketRepository.observeLastSuccessAt(),
        settingsRepository.settings,
        settingsRepository.dashboardConfig,
    ) { available, favorites, lastSync, settings, dashboard ->
        HomeInputs(available, favorites, lastSync, settings, dashboard)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        inputs,
        refreshing,
        sparklines,
    ) { in_, isRefreshing, sparks ->
        val dashboard = in_.dashboard
        val availableById = in_.available.associateBy { it.asset.id }

        // Featured: the user's explicit picks (unknown ids skipped), else
        // watchlist-first automatic selection.
        val featured = if (dashboard.featuredIsManual) {
            dashboard.featuredAssetIds.mapNotNull { availableById[it] }
        } else if (in_.favorites.isNotEmpty()) {
            in_.favorites.take(FEATURED_COUNT)
        } else {
            in_.available.sortedBy { it.asset.sortOrder }.take(FEATURED_COUNT)
        }

        val movers = in_.available
            .filter { it.quote?.changePercent != null }
            .sortedByDescending { it.quote!!.changePercent!!.toDouble() }
        val gainers = movers.take(MOVERS_COUNT)
        val losers = movers.asReversed().take(MOVERS_COUNT)

        HomeUiState(
            settings = DisplaySettings(
                language = in_.settings.language,
                digits = in_.settings.digitStyle,
                displayToman = in_.settings.displayToman,
            ),
            themeMode = in_.settings.themeMode,
            dynamicColors = in_.settings.dynamicColors,
            dashboard = dashboard,
            sections = dashboard.visibleSections(),
            featured = featured,
            sparklines = sparks,
            topGainers = gainers,
            topLosers = losers,
            watchlistPreview = in_.favorites.take(4),
            usdOfficial = in_.available.firstOrNull {
                it.asset.id == OFFICIAL_USD_ID && it.asset.unit == ir.cheghadr.core.model.PriceUnit.RIAL
            },
            lastSyncAt = in_.lastSyncAt,
            refreshing = isRefreshing,
            hasData = in_.available.any { it.quote != null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _oneShotEvent = MutableStateFlow<String?>(null)
    val oneShotEvent: StateFlow<String?> = _oneShotEvent.asStateFlow()

    /** All assets that can be picked as featured (for the customize screen). */
    val selectableAssets: StateFlow<List<AssetWithQuote>> =
        marketRepository.observeAvailable()
            .map { list -> list.sortedBy { it.asset.sortOrder } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            marketRepository.ensureSeeded()
            refresh()
        }
        // Reload mini-charts whenever the featured set changes (config edits
        // included) — observations come from this device's local history.
        viewModelScope.launch {
            uiState.map { it.featured.map { a -> a.asset.id } }
                .distinctUntilChanged()
                .collect { ids -> reloadSparklines(ids) }
        }
    }

    private suspend fun reloadSparklines(assetIds: List<String>) {
        runCatching { marketRepository.recentSparklines(assetIds) }
            .onSuccess { sparklines.value = it }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            runCatching { marketRepository.refresh() }
                .onFailure { _oneShotEvent.value = it.message }
            reloadSparklines(uiState.value.featured.map { it.asset.id })
            refreshing.value = false
        }
    }

    // ---- Dashboard customization (blueprint §7) ----

    fun setSectionVisible(section: DashboardSection, visible: Boolean) =
        updateDashboard { config ->
            config.copy(sections = config.sections.map {
                if (it.id == section) it.copy(visible = visible) else it
            })
        }

    /** Swaps a section with its neighbor in the render order. */
    fun moveSection(section: DashboardSection, up: Boolean) = updateDashboard { config ->
        val index = config.sections.indexOfFirst { it.id == section }
        if (index < 0) config
        else {
            val target = if (up) index - 1 else index + 1
            if (target !in config.sections.indices) config
            else config.copy(sections = config.sections.toMutableList().apply {
                val tmp = this[index]
                this[index] = this[target]
                this[target] = tmp
            })
        }
    }

    /** Toggles a featured pick; keeps selection order stable, honors the cap. */
    fun toggleFeaturedAsset(assetId: String) = updateDashboard { config ->
        val current = config.featuredAssetIds
        val next = if (assetId in current) {
            current - assetId
        } else if (current.size < DashboardConfig.MAX_FEATURED) {
            current + assetId
        } else {
            current
        }
        config.copy(featuredAssetIds = next)
    }

    /** Clears manual picks — back to automatic (watchlist-first) selection. */
    fun clearFeaturedSelection() = updateDashboard { it.copy(featuredAssetIds = emptyList()) }

    fun setDensity(density: DashboardDensity) =
        updateDashboard { it.copy(density = density) }

    fun setShowMiniCharts(show: Boolean) =
        updateDashboard { it.copy(showMiniCharts = show) }

    fun setShowChanges(show: Boolean) =
        updateDashboard { it.copy(showChanges = show) }

    fun resetDashboard() {
        viewModelScope.launch { settingsRepository.setDashboardConfig(DashboardConfig.DEFAULT) }
    }

    private fun updateDashboard(transform: (DashboardConfig) -> DashboardConfig) {
        viewModelScope.launch {
            val current = DashboardConfig.normalize(
                settingsRepository.dashboardConfig.firstOrNull() ?: DashboardConfig.DEFAULT,
            )
            settingsRepository.setDashboardConfig(DashboardConfig.normalize(transform(current)))
        }
    }

    fun consumeEvent() {
        _oneShotEvent.value = null
    }

    private companion object {
        const val FEATURED_COUNT = 6
        const val MOVERS_COUNT = 3

        /** Catalog id of the official rial-quoted US dollar. */
        const val OFFICIAL_USD_ID = "fx:USD"
    }
}
