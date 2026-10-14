package ir.cheghadr.feature.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheghadr.core.common.MoneyFormatter
import ir.cheghadr.core.common.PriceDisplay
import ir.cheghadr.core.model.AlertType

/**
 * Price alerts list + creation dialog. Includes the honest disclosure about
 * local (device-side) monitoring and Android scheduling constraints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var dialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.alerts_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogOpen = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.alerts_add))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.alerts_local_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (state.rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.alerts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.rows, key = { it.rule.id }) { row ->
                        AlertRuleCard(
                            row = row,
                            settings = state.settings,
                            onToggle = { viewModel.setEnabled(row.rule.id, it) },
                            onDelete = { viewModel.delete(row.rule.id) },
                        )
                    }
                }
            }
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.alerts_add)) },
            text = {
                AlertDraftForm(
                    draft = draft,
                    assets = state.assets,
                    settings = state.settings,
                    viewModel = viewModel,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.saveDraft()) dialogOpen = false
                    },
                ) { Text(stringResource(R.string.alerts_save)) }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(R.string.alerts_cancel))
                }
            },
        )
    }
}

@Composable
private fun AlertRuleCard(
    row: AlertRow,
    settings: ir.cheghadr.core.common.DisplaySettings,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.asset?.let { PriceDisplay.assetName(it.asset, settings) } ?: row.rule.assetId,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = typeLabel(row.rule.type) + ": " +
                        MoneyFormatter.formatPrice(row.rule.threshold, settings.digits) +
                        typeUnitSuffix(row.rule.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.rule.repeat) {
                    Text(
                        text = stringResource(R.string.alerts_repeat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Switch(checked = row.rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.alerts_delete),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertDraftForm(
    draft: AlertDraft,
    assets: List<ir.cheghadr.core.data.repo.AssetWithQuote>,
    settings: ir.cheghadr.core.common.DisplaySettings,
    viewModel: AlertsViewModel,
) {
    var assetMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExposedDropdownMenuBox(
            expanded = assetMenu,
            onExpandedChange = { assetMenu = it },
        ) {
            OutlinedTextField(
                value = assets.find { it.asset.id == draft.assetId }
                    ?.let { PriceDisplay.assetName(it.asset, settings) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.alerts_asset)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = assetMenu)
                },
            )
            ExposedDropdownMenu(
                expanded = assetMenu,
                onDismissRequest = { assetMenu = false },
            ) {
                assets.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(PriceDisplay.assetName(option.asset, settings), maxLines = 1) },
                        onClick = {
                            viewModel.updateDraft { it.copy(assetId = option.asset.id) }
                            assetMenu = false
                        },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = typeMenu,
            onExpandedChange = { typeMenu = it },
        ) {
            OutlinedTextField(
                value = typeLabel(draft.type),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.alerts_type)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenu)
                },
            )
            ExposedDropdownMenu(
                expanded = typeMenu,
                onDismissRequest = { typeMenu = false },
            ) {
                AlertType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(typeLabel(type)) },
                        onClick = {
                            viewModel.updateDraft { it.copy(type = type) }
                            typeMenu = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft.threshold,
            onValueChange = { value -> viewModel.updateDraft { it.copy(threshold = value) } },
            label = { Text(stringResource(R.string.alerts_threshold)) },
            isError = draft.threshold.isNotBlank() && runCatching {
                java.math.BigDecimal(draft.threshold.replace(',', '.'))
            }.getOrNull()?.signum() ?: 0 <= 0,
            supportingText = { Text(stringResource(R.string.alerts_threshold_invalid)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.cooldownMinutes,
            onValueChange = { value ->
                viewModel.updateDraft { it.copy(cooldownMinutes = value.filter { c -> c.isDigit() }) }
            },
            label = { Text(stringResource(R.string.alerts_cooldown)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        LabeledSwitch(
            label = stringResource(R.string.alerts_repeat),
            checked = draft.repeat,
            onCheckedChange = { value -> viewModel.updateDraft { it.copy(repeat = value) } },
        )
        LabeledSwitch(
            label = stringResource(R.string.alerts_allow_stale),
            checked = draft.allowStale,
            onCheckedChange = { value -> viewModel.updateDraft { it.copy(allowStale = value) } },
        )
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun typeLabel(type: AlertType): String = when (type) {
    AlertType.PRICE_ABOVE -> stringResource(R.string.alerts_type_above)
    AlertType.PRICE_BELOW -> stringResource(R.string.alerts_type_below)
    AlertType.PERCENT_UP -> stringResource(R.string.alerts_type_pct_up)
    AlertType.PERCENT_DOWN -> stringResource(R.string.alerts_type_pct_down)
}

private fun typeUnitSuffix(type: AlertType): String = when (type) {
    AlertType.PERCENT_UP, AlertType.PERCENT_DOWN -> "٪"
    else -> ""
}
