package ir.cheghadr.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.common.DisplaySettings
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.AssetWithQuote
import ir.cheghadr.core.data.repo.FavoritesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchlistUiState(
    val items: List<AssetWithQuote> = emptyList(),
    val settings: DisplaySettings = DisplaySettings(),
)

/** Watchlist management: reorder, remove; persists across restarts. */
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<WatchlistUiState> = combine(
        favoritesRepository.observeFavorites(),
        settingsRepository.settings,
    ) { favorites, settings ->
        WatchlistUiState(
            items = favorites,
            settings = DisplaySettings(
                language = settings.language,
                digits = settings.digitStyle,
                displayToman = settings.displayToman,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchlistUiState())

    fun move(assetId: String, up: Boolean) {
        viewModelScope.launch { favoritesRepository.move(assetId, up) }
    }

    fun remove(assetId: String) {
        viewModelScope.launch { favoritesRepository.toggle(assetId) }
    }
}
