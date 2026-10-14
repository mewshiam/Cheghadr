package ir.cheghadr.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.DashboardConfig
import ir.cheghadr.core.common.DigitStyle
import ir.cheghadr.core.common.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cheghadr_prefs",
)

/** All user preferences in one immutable snapshot. */
data class UserSettings(
    val language: AppLanguage = AppLanguage.PERSIAN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = false,
    val digitStyle: DigitStyle = DigitStyle.PERSIAN,
    /** Show IRR-quoted prices in toman (divide by 10, explicitly labeled). */
    val displayToman: Boolean = true,
    val refreshIntervalMinutes: Int = 30,
    val notificationsEnabled: Boolean = true,
    /** Quiet hours (minutes of day) during which alert notifications are suppressed. */
    val quietHoursStart: Int = 22 * 60,
    val quietHoursEnd: Int = 8 * 60,
    /** TGJU-compatible provider — disabled until the user configures an authorized endpoint. */
    val tgjuEnabled: Boolean = false,
    val tgjuBaseUrl: String = "",
    /** Optional CoinGecko demo key to relax public rate limits. */
    val coingeckoApiKey: String = "",
)

/**
 * DataStore-backed settings repository. Preferences persist offline and are
 * never uploaded anywhere.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val DIGIT_STYLE = stringPreferencesKey("digit_style")
        val DISPLAY_TOMAN = booleanPreferencesKey("display_toman")
        val REFRESH_MINUTES = intPreferencesKey("refresh_minutes")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val TGJU_ENABLED = booleanPreferencesKey("tgju_enabled")
        val TGJU_URL = stringPreferencesKey("tgju_url")
        val CG_KEY = stringPreferencesKey("cg_key")
        val DASHBOARD = stringPreferencesKey("dashboard_config")
    }

    val settings: Flow<UserSettings> = context.userPrefsDataStore.data.map { p ->
        UserSettings(
            language = p[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.PERSIAN,
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColors = p[Keys.DYNAMIC_COLORS] ?: false,
            digitStyle = p[Keys.DIGIT_STYLE]?.let { runCatching { DigitStyle.valueOf(it) }.getOrNull() }
                ?: DigitStyle.PERSIAN,
            displayToman = p[Keys.DISPLAY_TOMAN] ?: true,
            refreshIntervalMinutes = (p[Keys.REFRESH_MINUTES] ?: 30).coerceIn(15, 240),
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            quietHoursStart = p[Keys.QUIET_START] ?: (22 * 60),
            quietHoursEnd = p[Keys.QUIET_END] ?: (8 * 60),
            tgjuEnabled = p[Keys.TGJU_ENABLED] ?: false,
            tgjuBaseUrl = p[Keys.TGJU_URL] ?: "",
            coingeckoApiKey = p[Keys.CG_KEY] ?: "",
        )
    }

    /** User's dashboard layout (blueprint §7), always normalized on read. */
    val dashboardConfig: Flow<DashboardConfig> = context.userPrefsDataStore.data.map { p ->
        DashboardConfig.normalize(DashboardConfig.decodeOrNull(p[Keys.DASHBOARD]))
    }

    suspend fun setDashboardConfig(config: DashboardConfig) =
        edit { it[Keys.DASHBOARD] = DashboardConfig.encode(config) }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.LANGUAGE] = language.name }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColors(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLORS] = enabled }

    suspend fun setDigitStyle(style: DigitStyle) = edit { it[Keys.DIGIT_STYLE] = style.name }

    suspend fun setDisplayToman(enabled: Boolean) = edit { it[Keys.DISPLAY_TOMAN] = enabled }

    suspend fun setRefreshIntervalMinutes(minutes: Int) =
        edit { it[Keys.REFRESH_MINUTES] = minutes.coerceIn(15, 240) }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[Keys.NOTIFICATIONS] = enabled }

    suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) = edit {
        it[Keys.QUIET_START] = startMinutes.coerceIn(0, 24 * 60)
        it[Keys.QUIET_END] = endMinutes.coerceIn(0, 24 * 60)
    }

    suspend fun setTgjuEnabled(enabled: Boolean) = edit { it[Keys.TGJU_ENABLED] = enabled }

    suspend fun setTgjuBaseUrl(url: String) = edit { it[Keys.TGJU_URL] = url.trim() }

    suspend fun setCoinGeckoApiKey(key: String) = edit { it[Keys.CG_KEY] = key.trim() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.userPrefsDataStore.edit { block(it) }
    }
}
