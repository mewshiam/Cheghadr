package ir.cheghadr.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.DigitStyle
import ir.cheghadr.core.common.ThemeMode
import ir.cheghadr.core.data.export.BackupData
import ir.cheghadr.core.data.export.ExportSharer
import ir.cheghadr.core.data.export.toBackupSnapshot
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.preferences.UserSettings
import ir.cheghadr.core.data.repo.FavoritesRepository
import ir.cheghadr.core.data.repo.MarketRepository
import ir.cheghadr.core.model.ProviderStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val providerStatuses: List<ProviderStatus> = emptyList(),
)

/**
 * All user preferences. Language changes apply through the AndroidX per-app
 * locale API (works without reinstalling) and persist via DataStore.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val marketRepository: MarketRepository,
    private val favoritesRepository: FavoritesRepository,
    private val exportSharer: ExportSharer,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        marketRepository.observeProviderStatuses(),
    ) { settings, statuses ->
        SettingsUiState(settings = settings, providerStatuses = statuses)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(if (language == AppLanguage.PERSIAN) "fa" else "en"),
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColors(enabled) }
    }

    fun setDigitStyle(style: DigitStyle) {
        viewModelScope.launch { settingsRepository.setDigitStyle(style) }
    }

    fun setDisplayToman(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDisplayToman(enabled) }
    }

    fun setRefreshMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setRefreshIntervalMinutes(minutes) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch { settingsRepository.setQuietHours(startMinutes, endMinutes) }
    }

    fun setCoinGeckoKey(key: String) {
        viewModelScope.launch { settingsRepository.setCoinGeckoApiKey(key) }
    }

    fun setTgjuEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTgjuEnabled(enabled) }
    }

    fun setTgjuUrl(url: String) {
        viewModelScope.launch { settingsRepository.setTgjuBaseUrl(url) }
    }

    fun refreshNow() {
        viewModelScope.launch { marketRepository.refresh() }
    }

    /**
     * Local backup export (blueprint §15): settings + watchlist + dashboard
     * layout as JSON. Provider credentials are intentionally excluded —
     * backups must never leak API keys or custom endpoint URLs.
     */
    fun exportBackup() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val watchlist = favoritesRepository.observeFavorites().first()
            val dashboard = settingsRepository.dashboardConfig.first()
            val backup = BackupData(
                exportedAtEpochMs = System.currentTimeMillis(),
                settings = settings.toBackupSnapshot(),
                watchlistAssetIds = watchlist.map { it.asset.id },
                dashboard = dashboard,
            )
            val json = BackupData.encode(backup)
            val fileName = "cheghadr-backup-${System.currentTimeMillis()}.json"
            if (!exportSharer.shareFile(fileName, "application/json", json, "Cheghadr — backup")) {
                exportSharer.shareText(json, "Cheghadr — backup")
            }
        }
    }
}
