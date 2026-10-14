package ir.cheghadr.feature.alerts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.common.DisplaySettings
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.AssetWithQuote
import ir.cheghadr.core.data.repo.AlertRepository
import ir.cheghadr.core.data.repo.MarketRepository
import ir.cheghadr.core.model.AlertRule
import ir.cheghadr.core.model.AlertType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertRow(
    val rule: AlertRule,
    val asset: AssetWithQuote?,
)

data class AlertsUiState(
    val rows: List<AlertRow> = emptyList(),
    val assets: List<AssetWithQuote> = emptyList(),
    val settings: DisplaySettings = DisplaySettings(),
)

/** Pre-filled alert draft opened from an asset detail page. */
data class AlertDraft(
    val assetId: String = "",
    val type: AlertType = AlertType.PRICE_ABOVE,
    val threshold: String = "",
    val cooldownMinutes: String = "60",
    val repeat: Boolean = false,
    val allowStale: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alertRepository: AlertRepository,
    private val marketRepository: MarketRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Asset id passed when opening from a detail page; empty otherwise. */
    val preselectedAssetId: String = savedStateHandle.get<String>("assetId") ?: ""

    private val _draft = MutableStateFlow(AlertDraft())
    val draft: StateFlow<AlertDraft> = _draft

    val uiState: StateFlow<AlertsUiState> = combine(
        alertRepository.observeRules(),
        marketRepository.observeAvailable(),
        settingsRepository.settings,
    ) { rules, assets, settings ->
        val byId = assets.associateBy { it.asset.id }
        AlertsUiState(
            rows = rules.map { rule -> AlertRow(rule, byId[rule.assetId]) },
            assets = assets,
            settings = DisplaySettings(
                language = settings.language,
                digits = settings.digitStyle,
                displayToman = settings.displayToman,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())

    init {
        _draft.value = AlertDraft(assetId = preselectedAssetId)
    }

    fun updateDraft(transform: (AlertDraft) -> AlertDraft) {
        _draft.value = transform(_draft.value)
    }

    fun saveDraft(): Boolean {
        val d = _draft.value
        val threshold = runCatching { java.math.BigDecimal(d.threshold.replace(',', '.')) }.getOrNull()
        if (d.assetId.isBlank() || threshold == null || threshold.signum() <= 0) return false
        val cooldown = d.cooldownMinutes.toIntOrNull()?.coerceIn(0, 24 * 60) ?: 60
        viewModelScope.launch {
            alertRepository.save(
                AlertRule(
                    assetId = d.assetId,
                    type = d.type,
                    threshold = threshold,
                    cooldownMinutes = cooldown,
                    repeat = d.repeat,
                    allowStaleData = d.allowStale,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        _draft.value = AlertDraft(assetId = preselectedAssetId)
        return true
    }

    fun delete(id: Long) {
        viewModelScope.launch { alertRepository.delete(id) }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { alertRepository.setEnabled(id, enabled) }
    }
}
