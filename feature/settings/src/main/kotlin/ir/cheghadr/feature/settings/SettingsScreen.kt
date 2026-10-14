package ir.cheghadr.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.DigitStyle
import ir.cheghadr.core.common.ThemeMode
import ir.cheghadr.core.common.TimeFormat
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.ProviderStatus

/**
 * Settings: language, appearance, digits, display currency, refresh interval,
 * notifications/quiet hours, provider configuration & health, privacy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = state.settings
    var showPrivacy by remember { mutableStateOf(false) }
    var cgKeyDraft by remember(s.coingeckoApiKey) { mutableStateOf(s.coingeckoApiKey) }
    var tgjuUrlDraft by remember(s.tgjuBaseUrl) { mutableStateOf(s.tgjuBaseUrl) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = s.language == AppLanguage.PERSIAN,
                        onClick = { viewModel.setLanguage(AppLanguage.PERSIAN) },
                        label = { Text(stringResource(R.string.settings_language_fa)) },
                    )
                    FilterChip(
                        selected = s.language == AppLanguage.ENGLISH,
                        onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                        label = { Text(stringResource(R.string.settings_language_en)) },
                    )
                }
                Text(
                    text = stringResource(R.string.settings_apply_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = s.themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.settings_theme_system)) },
                    )
                    FilterChip(
                        selected = s.themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.settings_theme_light)) },
                    )
                    FilterChip(
                        selected = s.themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.settings_theme_dark)) },
                    )
                }
                ToggleRow(
                    label = stringResource(R.string.settings_dynamic_colors),
                    checked = s.dynamicColors,
                    onCheckedChange = viewModel::setDynamicColors,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_digits), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = s.digitStyle == DigitStyle.PERSIAN,
                        onClick = { viewModel.setDigitStyle(DigitStyle.PERSIAN) },
                        label = { Text(stringResource(R.string.settings_digits_fa)) },
                    )
                    FilterChip(
                        selected = s.digitStyle == DigitStyle.LATIN,
                        onClick = { viewModel.setDigitStyle(DigitStyle.LATIN) },
                        label = { Text(stringResource(R.string.settings_digits_latin)) },
                    )
                }
                ToggleRow(
                    label = stringResource(R.string.settings_display_toman),
                    checked = s.displayToman,
                    onCheckedChange = viewModel::setDisplayToman,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_data)) {
                Text(stringResource(R.string.settings_refresh_interval), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(15 to R.string.settings_refresh_15, 30 to R.string.settings_refresh_30, 60 to R.string.settings_refresh_60, 120 to R.string.settings_refresh_120)
                    options.forEach { (minutes, label) ->
                        FilterChip(
                            selected = s.refreshIntervalMinutes == minutes,
                            onClick = { viewModel.setRefreshMinutes(minutes) },
                            label = { Text(stringResource(label), maxLines = 1) },
                        )
                    }
                }
                // Local backup export (blueprint §15) — no secrets included.
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.settings_backup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = { viewModel.exportBackup() }) {
                    Text(stringResource(R.string.settings_backup_export))
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_notifications)) {
                ToggleRow(
                    label = stringResource(R.string.settings_notifications),
                    checked = s.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled,
                )
                Text(stringResource(R.string.settings_quiet_hours), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = s.quietHoursStart == 22 * 60 && s.quietHoursEnd == 8 * 60,
                        onClick = { viewModel.setQuietHours(22 * 60, 8 * 60) },
                        label = { Text(stringResource(R.string.settings_quiet_22_8)) },
                    )
                    FilterChip(
                        selected = s.quietHoursStart == 23 * 60 && s.quietHoursEnd == 7 * 60,
                        onClick = { viewModel.setQuietHours(23 * 60, 7 * 60) },
                        label = { Text(stringResource(R.string.settings_quiet_23_7)) },
                    )
                    FilterChip(
                        selected = s.quietHoursStart == 0 && s.quietHoursEnd == 0,
                        onClick = { viewModel.setQuietHours(0, 0) },
                        label = { Text(stringResource(R.string.settings_quiet_none)) },
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_providers)) {
                Text(stringResource(R.string.settings_provider_health), style = MaterialTheme.typography.titleSmall)
                state.providerStatuses.forEach { status ->
                    ProviderHealthRow(status)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cgKeyDraft,
                    onValueChange = { cgKeyDraft = it },
                    label = { Text(stringResource(R.string.settings_cg_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { viewModel.setCoinGeckoKey(cgKeyDraft) }) {
                            Text("✓")
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    label = stringResource(R.string.settings_tgju_enable),
                    checked = s.tgjuEnabled,
                    onCheckedChange = viewModel::setTgjuEnabled,
                )
                OutlinedTextField(
                    value = tgjuUrlDraft,
                    onValueChange = { tgjuUrlDraft = it },
                    label = { Text(stringResource(R.string.settings_tgju_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { viewModel.setTgjuUrl(tgjuUrlDraft) }) {
                            Text("✓")
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.settings_tgju_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                TextButton(onClick = { showPrivacy = true }) {
                    Text(stringResource(R.string.settings_privacy))
                }
                Text(
                    text = stringResource(R.string.settings_sources) + ": CoinGecko · ExchangeRate-API · Frankfurter (ECB) · " +
                        stringResource(R.string.settings_tgju_enable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.settings_privacy)) },
            text = { Text(stringResource(R.string.settings_privacy_body)) },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProviderHealthRow(status: ProviderStatus) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = providerName(status.providerId),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            val label = when {
                status.consecutiveFailures > 0 -> stringResource(R.string.settings_provider_failing, status.consecutiveFailures)
                status.lastSuccessAt != null -> stringResource(R.string.settings_provider_ok)
                else -> stringResource(R.string.settings_provider_never)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (status.consecutiveFailures > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        status.lastSuccessAt?.let {
            Text(
                text = TimeFormat.formatDateTime(it, false),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun providerName(id: ProviderId): String = when (id) {
    ProviderId.COIN_PAPRIKA -> "CoinPaprika"
    ProviderId.COINGECKO -> "CoinGecko"
    ProviderId.OPEN_ER_API -> "ExchangeRate-API"
    ProviderId.FRANKFURTER -> "Frankfurter (ECB)"
    ProviderId.GOLD_API -> "gold-api.com"
    ProviderId.TGJU -> "TGJU-compatible"
    ProviderId.APP -> "Local"
}
