package ir.cheghadr.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.DisplaySettings
import ir.cheghadr.core.common.DigitStyle
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val settings: DisplaySettings = DisplaySettings(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = false,
    val featured: List<AssetWithQuote> = emptyList(),
    val watchlistPreview: List<AssetWithQuote> = emptyList(),
    val lastSyncAt: Long? = null,
    val refreshing: Boolean = false,
    val hasData: Boolean = false,
)

/**
 * Home dashboard state. Renders cached data immediately and refreshes on
 * entry; the featured set falls back to a sensible default when the user has
 * no favorites yet.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        marketRepository.observeAvailable(),
        favoritesRepository.observeFavorites(),
        marketRepository.observeLastSuccessAt(),
        settingsRepository.settings,
        refreshing,
    ) { available, favorites, lastSync, settings, isRefreshing ->
        val featured = if (favorites.isNotEmpty()) {
            favorites.take(FEATURED_COUNT)
        } else {
            available.sortedBy { it.asset.sortOrder }.take(FEATURED_COUNT)
        }
        HomeUiState(
            settings = DisplaySettings(
                language = settings.language,
                digits = settings.digitStyle,
                displayToman = settings.displayToman,
            ),
            themeMode = settings.themeMode,
            dynamicColors = settings.dynamicColors,
            featured = featured,
            watchlistPreview = favorites.take(4),
            lastSyncAt = lastSync,
            refreshing = isRefreshing,
            hasData = available.any { it.quote != null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _oneShotEvent = MutableStateFlow<String?>(null)
    val oneShotEvent: StateFlow<String?> = _oneShotEvent.asStateFlow()

    init {
        viewModelScope.launch {
            marketRepository.ensureSeeded()
            refresh()
        }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            runCatching { marketRepository.refresh() }
                .onFailure { _oneShotEvent.value = it.message }
            refreshing.value = false
        }
    }

    fun consumeEvent() {
        _oneShotEvent.value = null
    }

    private companion object {
        const val FEATURED_COUNT = 6
    }
}
