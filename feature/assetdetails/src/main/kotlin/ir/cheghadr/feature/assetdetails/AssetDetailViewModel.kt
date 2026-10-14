package ir.cheghadr.feature.assetdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.common.Csv
import ir.cheghadr.core.common.DisplaySettings
import ir.cheghadr.core.data.export.ExportSharer
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.AssetWithQuote
import ir.cheghadr.core.data.repo.ChartData
import ir.cheghadr.core.data.repo.FavoritesRepository
import ir.cheghadr.core.data.repo.MarketRepository
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.HistoryRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val asset: AssetWithQuote? = null,
    val isFavorite: Boolean = false,
    val settings: DisplaySettings = DisplaySettings(),
    val range: HistoryRange = HistoryRange.DAY_30,
    val chart: ChartUiState = ChartUiState(),
    val localHistory: List<HistoryPoint> = emptyList(),
)

data class ChartUiState(
    val loading: Boolean = false,
    val data: ChartData = ChartData.Insufficient,
)

/**
 * Asset detail: live quote, interactive historical chart with range selection,
 * statistics, favorite toggle and alert/share actions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val marketRepository: MarketRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
    private val exportSharer: ExportSharer,
) : ViewModel() {

    val assetId: String = savedStateHandle.get<String>("assetId") ?: ""

    private val range = MutableStateFlow(HistoryRange.DAY_30)
    private val chart = MutableStateFlow(ChartUiState())

    val uiState: StateFlow<DetailUiState> = combine(
        marketRepository.observeAsset(assetId),
        favoritesRepository.observeFavorites(),
        settingsRepository.settings,
        range,
        chart,
    ) { asset, favorites, settings, r, c ->
        DetailUiState(
            asset = asset,
            isFavorite = favorites.any { it.asset.id == assetId },
            settings = DisplaySettings(
                language = settings.language,
                digits = settings.digitStyle,
                displayToman = settings.displayToman,
            ),
            range = r,
            chart = c,
            localHistory = emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        viewModelScope.launch { marketRepository.ensureSeeded() }
        loadChart()
        viewModelScope.launch {
            range.collect { loadChart() }
        }
    }

    fun selectRange(value: HistoryRange) {
        range.value = value
    }

    private fun loadChart() {
        viewModelScope.launch {
            chart.value = ChartUiState(loading = true)
            val asset = marketRepository.getAsset(assetId) ?: return@launch
            val data = marketRepository.chartData(asset, range.value.days)
            chart.value = ChartUiState(loading = false, data = data)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch { favoritesRepository.toggle(assetId) }
    }

    /** Shareable text summary with source and timestamp disclosure. */
    suspend fun shareText(): String? {
        val asset = marketRepository.getAsset(assetId) ?: return null
        val quote = marketRepository.getQuote(assetId) ?: return null
        val settings = DisplaySettings()
        val providerName = when (asset.providerId) {
            ir.cheghadr.core.model.ProviderId.COIN_PAPRIKA -> "CoinPaprika"
            ir.cheghadr.core.model.ProviderId.COINGECKO -> "CoinGecko"
            ir.cheghadr.core.model.ProviderId.OPEN_ER_API -> "ExchangeRate-API"
            ir.cheghadr.core.model.ProviderId.FRANKFURTER -> "Frankfurter (ECB)"
            ir.cheghadr.core.model.ProviderId.GOLD_API -> "gold-api.com"
            ir.cheghadr.core.model.ProviderId.TGJU -> "TGJU-compatible source"
            ir.cheghadr.core.model.ProviderId.APP -> "local computation"
        }
        return "${asset.nameFa}: ${ir.cheghadr.core.common.PriceDisplay.priceNumber(asset, quote, settings)} " +
            "(${ir.cheghadr.core.common.PriceDisplay.changeLabel(quote, settings) ?: "–"}) — " +
            "via «چقدر؟» from $providerName"
    }

    /**
     * CSV export of the currently displayed series (blueprint §15). Only real
     * points are exported — provider history or this device's observations,
     * with a `source` column disclosing which. Nothing is interpolated.
     */
    fun exportChartCsv() {
        viewModelScope.launch {
            val asset = marketRepository.getAsset(assetId) ?: return@launch
            val (points, source) = when (val data = chart.value.data) {
                is ChartData.ProviderSeries -> data.points to "provider:${data.providerId.name.lowercase()}"
                is ChartData.LocalSeries -> data.points to "local-observations"
                ChartData.Insufficient -> emptyList<HistoryPoint>() to "none"
            }
            if (points.isEmpty()) return@launch
            val csv = Csv.build(
                header = listOf("asset_id", "timestamp_iso", "timestamp_epoch_ms", "price", "source"),
                rows = points.map { p ->
                    listOf(
                        asset.id,
                        java.time.Instant.ofEpochMilli(p.timestamp).toString(),
                        p.timestamp.toString(),
                        p.price.toPlainString(),
                        source,
                    )
                },
            )
            val safeId = asset.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val fileName = "cheghadr-history-$safeId-${System.currentTimeMillis()}.csv"
            if (!exportSharer.shareFile(fileName, "text/csv", csv, "Cheghadr — history")) {
                exportSharer.shareText(csv, "Cheghadr — history")
            }
        }
    }
}
