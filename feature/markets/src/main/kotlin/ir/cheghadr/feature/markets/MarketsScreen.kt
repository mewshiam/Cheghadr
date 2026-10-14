package ir.cheghadr.feature.markets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheghadr.core.common.FreshnessClassifier
import ir.cheghadr.core.common.PriceDisplay
import ir.cheghadr.core.designsystem.MarketRow
import ir.cheghadr.core.designsystem.SkeletonRow
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.DataFreshness

private val categories = listOf(
    AssetCategory.CURRENCY,
    AssetCategory.CRYPTO,
    AssetCategory.GOLD,
    AssetCategory.COIN,
    AssetCategory.STOCK,
    AssetCategory.COMMODITY,
)

/**
 * Markets explorer: category tabs, sorting, pull-to-refresh and honest
 * unavailable states per category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(
    onOpenAsset: (String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: MarketsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.markets_title)) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_title),
                        )
                    }
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = stringResource(R.string.markets_sort),
                        )
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.markets_sort_default)) },
                            onClick = { viewModel.selectSort(MarketSort.DEFAULT); sortMenuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.markets_sort_name)) },
                            onClick = { viewModel.selectSort(MarketSort.NAME); sortMenuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.markets_sort_price)) },
                            onClick = { viewModel.selectSort(MarketSort.PRICE); sortMenuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.markets_sort_change)) },
                            onClick = { viewModel.selectSort(MarketSort.CHANGE_ABS); sortMenuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.markets_sort_change_pct)) },
                            onClick = { viewModel.selectSort(MarketSort.CHANGE_PCT); sortMenuOpen = false },
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.markets_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val selected = categories.indexOf(state.category).coerceAtLeast(0)
            ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                categories.forEach { category ->
                    Tab(
                        selected = category == state.category,
                        onClick = { viewModel.selectCategory(category) },
                        text = { Text(categoryLabel(category)) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
            ) {
                if (state.items.isEmpty()) {
                    // Skeleton placeholders while the category loads.
                    repeat(8) { SkeletonRow() }
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState) {
                        items(state.items, key = { it.asset.id }) { item ->
                            val unavailableMessage = if (!item.asset.available) {
                                if (state.settings.language == ir.cheghadr.core.common.AppLanguage.PERSIAN) {
                                    item.asset.unavailableReasonFa
                                } else {
                                    item.asset.unavailableReasonEn
                                }
                            } else {
                                null
                            }
                            val freshness = FreshnessClassifier.classify(
                                item.quote,
                                System.currentTimeMillis(),
                                item.asset.category == AssetCategory.CRYPTO,
                            )
                            val staleLabel = when (freshness) {
                                DataFreshness.FRESH -> null
                                DataFreshness.STALE -> stringResource(ir.cheghadr.core.designsystem.R.string.freshness_stale)
                                DataFreshness.VERY_STALE -> stringResource(ir.cheghadr.core.designsystem.R.string.freshness_very_stale)
                                DataFreshness.UNKNOWN -> null
                            }
                            MarketRow(
                                name = PriceDisplay.assetName(item.asset, state.settings),
                                symbol = item.asset.symbol,
                                priceText = PriceDisplay.priceNumber(item.asset, item.quote, state.settings),
                                unitWord = PriceDisplay.unitWord(item.asset, state.settings),
                                changeText = PriceDisplay.changeLabel(item.quote, state.settings),
                                changeUp = PriceDisplay.changeIsUp(item.quote),
                                onClick = { onOpenAsset(item.asset.id) },
                                staleLabel = staleLabel,
                                unavailableMessage = unavailableMessage,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: AssetCategory): String = when (category) {
    AssetCategory.CURRENCY -> stringResource(R.string.markets_tab_currency)
    AssetCategory.GOLD -> stringResource(R.string.markets_tab_gold)
    AssetCategory.COIN -> stringResource(R.string.markets_tab_coin)
    AssetCategory.CRYPTO -> stringResource(R.string.markets_tab_crypto)
    AssetCategory.STOCK -> stringResource(R.string.markets_tab_stock)
    AssetCategory.COMMODITY -> stringResource(R.string.markets_tab_commodity)
}
