package ir.cheghadr.feature.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.common.DisplaySettings
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.AssetWithQuote
import ir.cheghadr.core.data.repo.MarketRepository
import ir.cheghadr.core.data.repo.PortfolioRepository
import ir.cheghadr.core.model.PortfolioCalculator
import ir.cheghadr.core.model.Transaction
import ir.cheghadr.core.model.TxSide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class PositionRow(
    val asset: AssetWithQuote,
    val position: PortfolioCalculator.Position,
    val currentValue: BigDecimal?,
    val unrealized: BigDecimal?,
)

data class PortfolioUiState(
    val positions: List<PositionRow> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val assets: List<AssetWithQuote> = emptyList(),
    val settings: DisplaySettings = DisplaySettings(),
    val totalValue: BigDecimal? = null,
    val totalUnrealized: BigDecimal? = null,
    val totalRealized: BigDecimal = BigDecimal.ZERO,
)

/**
 * Local-only portfolio tracking: average-cost accounting, unrealized/realized
 * P/L, per-asset positions and transaction history. Never synchronized.
 */
@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val marketRepository: MarketRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    data class Draft(
        val assetId: String = "",
        val side: TxSide = TxSide.BUY,
        val quantity: String = "",
        val price: String = "",
        val fee: String = "0",
        val note: String = "",
    )

    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft

    val uiState: StateFlow<PortfolioUiState> = combine(
        portfolioRepository.observePositions(),
        portfolioRepository.observeTransactions(),
        marketRepository.observeAvailable(),
        settingsRepository.settings,
    ) { positions, transactions, assets, settings ->
        val byId = assets.associateBy { it.asset.id }
        val rows = positions.mapNotNull { (assetId, position) ->
            val asset = byId[assetId] ?: return@mapNotNull null
            PositionRow(
                asset = asset,
                position = position,
                currentValue = PortfolioCalculator.currentValue(position, asset.quote?.price),
                unrealized = PortfolioCalculator.unrealized(position, asset.quote?.price),
            )
        }
        PortfolioUiState(
            positions = rows,
            transactions = transactions,
            assets = assets,
            settings = DisplaySettings(
                language = settings.language,
                digits = settings.digitStyle,
                displayToman = settings.displayToman,
            ),
            totalValue = rows.mapNotNull { it.currentValue }.reduceOrNull { acc, v -> acc.add(v) },
            totalUnrealized = rows.mapNotNull { it.unrealized }.reduceOrNull { acc, v -> acc.add(v) },
            totalRealized = positions.values.map { it.realized }.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    init {
        viewModelScope.launch { marketRepository.ensureSeeded() }
    }

    fun updateDraft(transform: (Draft) -> Draft) {
        _draft.value = transform(_draft.value)
    }

    fun saveDraft(): Boolean {
        val d = _draft.value
        val qty = runCatching { BigDecimal(d.quantity.replace(',', '.')) }.getOrNull()
        val price = runCatching { BigDecimal(d.price.replace(',', '.')) }.getOrNull()
        val fee = runCatching { BigDecimal(d.fee.ifBlank { "0" }.replace(',', '.')) }.getOrNull() ?: BigDecimal.ZERO
        if (d.assetId.isBlank() || qty == null || price == null || qty.signum() <= 0 || price.signum() <= 0) {
            return false
        }
        viewModelScope.launch {
            portfolioRepository.add(
                Transaction(
                    assetId = d.assetId,
                    side = d.side,
                    quantity = qty,
                    pricePerUnit = price,
                    fee = fee,
                    timestamp = System.currentTimeMillis(),
                    note = d.note.takeIf { it.isNotBlank() },
                ),
            )
        }
        _draft.value = Draft()
        return true
    }

    fun delete(id: Long) {
        viewModelScope.launch { portfolioRepository.delete(id) }
    }
}
