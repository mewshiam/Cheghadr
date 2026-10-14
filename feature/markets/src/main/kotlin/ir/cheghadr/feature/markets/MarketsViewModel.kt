package ir.cheghadr.feature.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.common.DisplaySettings
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.AssetWithQuote
import ir.cheghadr.core.data.repo.MarketRepository
import ir.cheghadr.core.model.AssetCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MarketSort { DEFAULT, NAME, PRICE, CHANGE_ABS, CHANGE_PCT }

data class MarketsUiState(
    val category: AssetCategory = AssetCategory.CRYPTO,
    val sort: MarketSort = MarketSort.DEFAULT,
    val items: List<AssetWithQuote> = emptyList(),
    val settings: DisplaySettings = DisplaySettings(),
    val refreshing: Boolean = false,
)

/**
 * Markets explorer with category tabs and sorting. The category flow is
 * switched with flatMapLatest; every list renders cached quotes instantly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MarketsViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val category = MutableStateFlow(AssetCategory.CRYPTO)
    private val sort = MutableStateFlow(MarketSort.DEFAULT)
    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<MarketsUiState> = combine(
        category.flatMapLatest { marketRepository.observeCategory(it) },
        combine(category, sort) { c, s -> c to s },
        settingsRepository.settings,
        refreshing,
    ) { items, catAndSort, settings, isRefreshing ->
        val (cat, s) = catAndSort
        MarketsUiState(
            category = cat,
            sort = s,
            items = sortItems(items, s),
            settings = DisplaySettings(
                language = settings.language,
                digits = settings.digitStyle,
                displayToman = settings.displayToman,
            ),
            refreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketsUiState())

    fun selectCategory(value: AssetCategory) {
        category.value = value
    }

    fun selectSort(value: MarketSort) {
        sort.value = value
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            runCatching { marketRepository.refresh() }
            refreshing.value = false
        }
    }

    private fun sortItems(items: List<AssetWithQuote>, sort: MarketSort): List<AssetWithQuote> =
        when (sort) {
            MarketSort.DEFAULT -> items
            MarketSort.NAME -> items.sortedBy { it.asset.nameFa }
            MarketSort.PRICE -> items.sortedByDescending { it.quote?.price }
            MarketSort.CHANGE_ABS -> items.sortedByDescending { it.quote?.changeAbs }
            MarketSort.CHANGE_PCT -> items.sortedByDescending { it.quote?.changePercent }
        }
}

data class SearchUiState(
    val query: String = "",
    val results: List<AssetWithQuote> = emptyList(),
    val history: List<String> = emptyList(),
    val settings: DisplaySettings = DisplaySettings(),
    val searching: Boolean = false,
)

/** Universal search across Persian and English names, tickers and aliases. */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val results = MutableStateFlow<List<AssetWithQuote>>(emptyList())
    private val searching = MutableStateFlow(false)

    val uiState: StateFlow<SearchUiState> = combine(
        query.debounce(250),
        results,
        marketRepository.observeSearchHistory(),
        settingsRepository.settings,
        searching,
    ) { q, res, history, settings, isSearching ->
        SearchUiState(
            query = q,
            results = res,
            history = history,
            settings = DisplaySettings(
                language = settings.language,
                digits = settings.digitStyle,
                displayToman = settings.displayToman,
            ),
            searching = isSearching,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch {
            query.debounce(250).collect { q ->
                if (q.length >= 2) {
                    searching.value = true
                    results.value = runCatching { marketRepository.search(q) }.getOrDefault(emptyList())
                    marketRepository.recordSearch(q)
                    searching.value = false
                } else {
                    results.value = emptyList()
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun clearHistory() {
        viewModelScope.launch { marketRepository.clearSearchHistory() }
    }
}
