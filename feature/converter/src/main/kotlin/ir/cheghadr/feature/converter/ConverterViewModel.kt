package ir.cheghadr.feature.converter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.common.DisplaySettings
import ir.cheghadr.core.common.FreshnessClassifier
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.AssetWithQuote
import ir.cheghadr.core.data.repo.MarketRepository
import ir.cheghadr.core.model.DataFreshness
import ir.cheghadr.core.model.PriceUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class CurrencyTabState(
    val amountInput: String = "1",
    val fromId: String = "fx:USD",
    val toId: String = "fx:EUR",
    val assets: List<AssetWithQuote> = emptyList(),
    val settings: DisplaySettings = DisplaySettings(),
    val lastUpdate: Long? = null,
    val stale: Boolean = false,
)

data class GoldTabState(
    val gram18Input: String = "",
    val makingInput: String = "0",
    val taxInput: String = "0",
    val discountInput: String = "0",
    val liveGram18: BigDecimal? = null,
    val settings: DisplaySettings = DisplaySettings(),
)

data class ConverterUiState(
    val tab: Int = 0,
    val currency: CurrencyTabState = CurrencyTabState(),
    val gold: GoldTabState = GoldTabState(),
)

/**
 * Currency + gold calculator. Conversions use the cached official/crypto
 * quotes through the pure CurrencyConverter; offline use is supported with a
 * visible staleness warning.
 */
@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val tab = MutableStateFlow(0)
    private val amountInput = MutableStateFlow("1")
    private val fromId = MutableStateFlow("fx:USD")
    private val toId = MutableStateFlow("fx:EUR")
    private val gram18Input = MutableStateFlow("")
    private val makingInput = MutableStateFlow("0")
    private val taxInput = MutableStateFlow("0")
    private val discountInput = MutableStateFlow("0")

    val uiState: StateFlow<ConverterUiState> = combine(
        combine(tab, amountInput, fromId, toId) { t, a, f, to -> Quad(t, a, f, to) },
        marketRepository.observeAvailable(),
        settingsRepository.settings,
        combine(gram18Input, makingInput, taxInput, discountInput) { g, m, tx, d -> Quad(g, m, tx, d) },
    ) { quad, assets, settings, goldInputs ->
        val convertible = assets.filter {
            it.asset.unit in setOf(PriceUnit.RIAL, PriceUnit.USD, PriceUnit.EUR) ||
                it.asset.id.startsWith("fxint:")
        }
        val usdIrr = convertible.firstOrNull { it.asset.id == "fx:USD" }?.quote
        ConverterUiState(
            tab = quad.first,
            currency = CurrencyTabState(
                amountInput = quad.second,
                fromId = quad.third,
                toId = quad.fourth,
                assets = convertible,
                settings = DisplaySettings(
                    language = settings.language,
                    digits = settings.digitStyle,
                    displayToman = settings.displayToman,
                ),
                lastUpdate = assets.mapNotNull { it.quote?.fetchTime }.maxOrNull(),
                stale = assets.any {
                    FreshnessClassifier.classify(it.quote, System.currentTimeMillis()) != DataFreshness.FRESH
                },
            ),
            gold = GoldTabState(
                gram18Input = goldInputs.first,
                makingInput = goldInputs.second,
                taxInput = goldInputs.third,
                discountInput = goldInputs.fourth,
                liveGram18 = assets.firstOrNull { it.asset.id == "gold:GRAM18" }?.quote?.price,
                settings = DisplaySettings(
                    language = settings.language,
                    digits = settings.digitStyle,
                    displayToman = settings.displayToman,
                ),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConverterUiState())

    init {
        viewModelScope.launch {
            marketRepository.ensureSeeded()
            // Prefill the gold gram price once a live quote exists.
            marketRepository.observeAsset("gold:GRAM18").collect { item ->
                item?.quote?.let { quote ->
                    if (gram18Input.value.isBlank()) {
                        gram18Input.value = quote.price.setScale(0, RoundingMode.HALF_UP).toPlainString()
                    }
                }
            }
        }
    }

    fun selectTab(index: Int) {
        tab.value = index
    }

    fun onAmountChange(value: String) {
        amountInput.value = value
    }

    fun onFromChange(assetId: String) {
        fromId.value = assetId
    }

    fun onToChange(assetId: String) {
        toId.value = assetId
    }

    fun swap() {
        val f = fromId.value
        fromId.value = toId.value
        toId.value = f
    }

    fun onGram18Change(value: String) {
        gram18Input.value = value
    }

    fun onMakingChange(value: String) {
        makingInput.value = value
    }

    fun onTaxChange(value: String) {
        taxInput.value = value
    }

    fun onDiscountChange(value: String) {
        discountInput.value = value
    }

    /** Current conversion result; null when quotes are missing or input invalid. */
    fun conversionResult(state: ConverterUiState): BigDecimal? {
        val amount = runCatching { BigDecimal(state.currency.amountInput.replace(',', '.')) }.getOrNull()
            ?: return null
        if (amount.signum() < 0) return null
        val from = state.currency.assets.find { it.asset.id == state.currency.fromId } ?: return null
        val to = state.currency.assets.find { it.asset.id == state.currency.toId } ?: return null
        val usdIrr = state.currency.assets.find { it.asset.id == "fx:USD" }?.quote
        return CurrencyConverter.convert(amount, from.asset to from.quote, to.asset to to.quote, usdIrr)
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
