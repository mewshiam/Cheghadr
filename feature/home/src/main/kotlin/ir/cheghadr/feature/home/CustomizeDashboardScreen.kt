package ir.cheghadr.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.DashboardConfig
import ir.cheghadr.core.common.DashboardDensity
import ir.cheghadr.core.common.DashboardSection
import ir.cheghadr.core.common.PriceDisplay

/**
 * Dashboard customization (blueprint §7): show/hide and reorder sections,
 * pick featured assets, choose density, toggle mini charts and change
 * badges, reset the layout. Everything persists locally — no account.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomizeDashboardScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val assets by viewModel.selectableAssets.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }
    var assetFilter by remember { mutableStateOf("") }

    val persian = state.settings.language == AppLanguage.PERSIAN

    // Resolve all section labels once in composable context.
    val sectionLabels = mapOf(
        DashboardSection.FEATURED to stringResource(R.string.dash_section_featured),
        DashboardSection.QUICK_CONVERTER to stringResource(R.string.dash_section_converter),
        DashboardSection.WATCHLIST_PREVIEW to stringResource(R.string.dash_section_watchlist),
        DashboardSection.MARKET_OVERVIEW to stringResource(R.string.dash_section_overview),
    )
    val sectionLabel: (DashboardSection) -> String = { sectionLabels[it] ?: "" }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.home_customize)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.dash_back),
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            // ---- Sections: visibility + order ----
            item {
                Subhead(text = stringResource(R.string.dash_sections))
            }
            items(state.dashboard.sections, key = { it.id.name }) { sectionState ->
                val position = state.dashboard.sections.indexOf(sectionState)
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sectionLabel(sectionState.id),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.dash_section_position, position + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveSection(sectionState.id, up = true) },
                            enabled = position > 0,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = stringResource(R.string.dash_move_up),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveSection(sectionState.id, up = false) },
                            enabled = position < state.dashboard.sections.lastIndex,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = stringResource(R.string.dash_move_down),
                            )
                        }
                        Switch(
                            checked = sectionState.visible,
                            onCheckedChange = { viewModel.setSectionVisible(sectionState.id, it) },
                        )
                    }
                }
            }

            // ---- Featured assets ----
            item {
                Subhead(
                    text = stringResource(
                        R.string.dash_featured_assets,
                        state.dashboard.featuredAssetIds.size,
                        DashboardConfig.MAX_FEATURED,
                    ),
                )
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !state.dashboard.featuredIsManual,
                        onClick = { viewModel.clearFeaturedSelection() },
                        label = { Text(stringResource(R.string.dash_featured_auto)) },
                    )
                }
                if (state.dashboard.featuredIsManual) {
                    Text(
                        text = state.dashboard.featuredAssetIds.joinToString(" · ") { id ->
                            assets.firstOrNull { it.asset.id == id }?.let {
                                PriceDisplay.assetName(it.asset, state.settings)
                            } ?: id
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                OutlinedTextField(
                    value = assetFilter,
                    onValueChange = { assetFilter = it },
                    placeholder = { Text(stringResource(R.string.dash_filter_assets)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val filter = assetFilter.trim()
            val candidates = assets.filter { item ->
                filter.isEmpty() ||
                    item.asset.nameFa.contains(filter, ignoreCase = true) ||
                    item.asset.nameEn.contains(filter, ignoreCase = true) ||
                    item.asset.symbol.contains(filter, ignoreCase = true)
            }
            items(candidates, key = { it.asset.id }) { item ->
                val checked = item.asset.id in state.dashboard.featuredAssetIds
                val full = !checked && state.dashboard.featuredAssetIds.size >= DashboardConfig.MAX_FEATURED
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        enabled = !full,
                        onCheckedChange = { viewModel.toggleFeaturedAsset(item.asset.id) },
                    )
                    Text(
                        text = PriceDisplay.assetName(item.asset, state.settings) +
                            "  (" + item.asset.symbol + ")",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            // ---- Density ----
            item {
                Subhead(text = stringResource(R.string.dash_density))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.dashboard.density == DashboardDensity.COMFORTABLE,
                        onClick = { viewModel.setDensity(DashboardDensity.COMFORTABLE) },
                        label = { Text(stringResource(R.string.dash_density_comfortable)) },
                    )
                    FilterChip(
                        selected = state.dashboard.density == DashboardDensity.COMPACT,
                        onClick = { viewModel.setDensity(DashboardDensity.COMPACT) },
                        label = { Text(stringResource(R.string.dash_density_compact)) },
                    )
                }
            }

            // ---- Toggles ----
            item {
                Subhead(text = stringResource(R.string.dash_display))
                ToggleRow(
                    label = stringResource(R.string.dash_minicharts),
                    subtitle = stringResource(R.string.dash_minicharts_hint),
                    checked = state.dashboard.showMiniCharts,
                    onChange = { viewModel.setShowMiniCharts(it) },
                )
                ToggleRow(
                    label = stringResource(R.string.dash_show_changes),
                    subtitle = stringResource(R.string.dash_show_changes_hint),
                    checked = state.dashboard.showChanges,
                    onChange = { viewModel.setShowChanges(it) },
                )
            }

            // ---- Reset ----
            item {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.dash_reset)) }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.dash_reset)) },
            text = { Text(stringResource(R.string.dash_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetDashboard()
                        showResetDialog = false
                    },
                ) { Text(stringResource(R.string.dash_reset_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.dash_reset_no))
                }
            },
        )
    }
}

@Composable
private fun Subhead(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
