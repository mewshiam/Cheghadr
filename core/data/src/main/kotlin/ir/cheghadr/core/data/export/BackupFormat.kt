package ir.cheghadr.core.data.export

import ir.cheghadr.core.common.DashboardConfig
import ir.cheghadr.core.data.preferences.UserSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Local backup format (blueprint §15: "watchlist and settings backup/export").
 *
 * Privacy: provider credentials (CoinGecko key, TGJU endpoint) are deliberately
 * **not** included — backups never leave the device except through the user's
 * own explicit share action.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAtEpochMs: Long,
    val settings: SettingsSnapshot,
    /** Canonical asset ids of the watchlist, in display order. */
    val watchlistAssetIds: List<String>,
    val dashboard: DashboardConfig,
) {
    @Serializable
    data class SettingsSnapshot(
        val language: String,
        val themeMode: String,
        val dynamicColors: Boolean,
        val digitStyle: String,
        val displayToman: Boolean,
        val refreshIntervalMinutes: Int,
        val notificationsEnabled: Boolean,
        val quietHoursStartMinutes: Int,
        val quietHoursEndMinutes: Int,
    )

    companion object {
        const val CURRENT_VERSION = 1

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(data: BackupData): String = json.encodeToString(serializer(), data)

        fun decodeOrNull(raw: String): BackupData? =
            runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
    }
}

/** Maps live settings into the backup snapshot (no secrets, see class doc). */
fun UserSettings.toBackupSnapshot() = BackupData.SettingsSnapshot(
    language = language.name,
    themeMode = themeMode.name,
    dynamicColors = dynamicColors,
    digitStyle = digitStyle.name,
    displayToman = displayToman,
    refreshIntervalMinutes = refreshIntervalMinutes,
    notificationsEnabled = notificationsEnabled,
    quietHoursStartMinutes = quietHoursStart,
    quietHoursEndMinutes = quietHoursEnd,
)
