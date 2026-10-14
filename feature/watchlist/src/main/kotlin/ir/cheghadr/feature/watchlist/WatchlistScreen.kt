package ir.cheghadr.feature.watchlist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.FreshnessClassifier
import ir.cheghadr.core.common.PriceDisplay
import ir.cheghadr.core.designsystem.EmptyState
import ir.cheghadr.core.designsystem.MarketRow
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.DataFreshness

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onOpenAsset: (String) -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.watchlist_title)) })
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.watchlist_empty),
                message = "",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.items, key = { it.asset.id }) { item ->
                    val persian = state.settings.language == AppLanguage.PERSIAN
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
                        trailing = {
                            Row {
                                IconButton(onClick = { viewModel.move(item.asset.id, up = true) }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.watchlist_move_up),
                                    )
                                }
                                IconButton(onClick = { viewModel.move(item.asset.id, up = false) }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.watchlist_move_down),
                                    )
                                }
                                IconButton(onClick = { viewModel.remove(item.asset.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.watchlist_remove),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
