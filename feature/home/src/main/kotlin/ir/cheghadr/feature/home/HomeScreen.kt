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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.TimeFormat
import ir.cheghadr.core.common.PriceDisplay
import ir.cheghadr.core.common.FreshnessClassifier
import ir.cheghadr.core.data.repo.AssetWithQuote
import ir.cheghadr.core.designsystem.ChangeBadge
import ir.cheghadr.core.designsystem.EmptyState
import ir.cheghadr.core.designsystem.MarketRow
import ir.cheghadr.core.designsystem.SectionHeader
import ir.cheghadr.core.model.DataFreshness
import ir.cheghadr.core.model.PriceUnit
import java.time.Instant
import java.time.ZoneId

/**
 * Home dashboard: minimal by default, personalized through the watchlist.
 * Renders cached data immediately; the header exposes refresh and search.
 */
@Composable
fun HomeScreen(
    onOpenAsset: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenConverter: () -> Unit,
    onOpenWatchlist: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val persian = state.settings.language == AppLanguage.PERSIAN

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Compact header with app identity, search and refresh.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.home_search),
                )
            }
            IconButton(onClick = { viewModel.refresh() }) {
                if (state.refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp).width(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.home_refresh),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            StatusLine(state = state, persian = persian)

            if (!state.hasData && state.featured.isNotEmpty() && state.featured.all { it.quote == null }) {
                EmptyState(
                    title = stringResource(R.string.home_no_data),
                    message = "",
                )
                OutlinedButton(
                    onClick = { viewModel.refresh() },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp),
                ) { Text(stringResource(R.string.home_retry)) }
            }

            SectionHeader(
                title = stringResource(R.string.home_featured),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FeaturedGrid(
                items = state.featured,
                settings = state.settings,
                onOpenAsset = onOpenAsset,
            )

            // Quick converter card.
            val usd = state.featured.firstOrNull { it.asset.unit == PriceUnit.RIAL && it.asset.symbol == "USD" }
            if (usd?.quote != null) {
                QuickConverterCard(
                    usd = usd,
                    state = state,
                    persian = persian,
                    onOpenConverter = onOpenConverter,
                )
            }

            SectionHeader(
                title = stringResource(R.string.home_watchlist),
                modifier = Modifier.padding(horizontal = 16.dp),
                actionText = stringResource(R.string.home_manage),
                onAction = onOpenWatchlist,
            )
            if (state.watchlistPreview.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_watchlist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            } else {
                state.watchlistPreview.forEach { item ->
                    WatchlistRow(item = item, state = state, persian = persian, onOpenAsset = onOpenAsset)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(state: HomeUiState, persian: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = when {
            state.refreshing -> stringResource(R.string.home_updating)
            state.lastSyncAt != null -> stringResource(
                R.string.home_last_update,
                TimeFormat.formatDateTime(state.lastSyncAt, persian),
            )
            else -> stringResource(R.string.home_never_updated)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FeaturedGrid(
    items: List<AssetWithQuote>,
    settings: ir.cheghadr.core.common.DisplaySettings,
    onOpenAsset: (String) -> Unit,
) {
    items.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rowItems.forEach { item ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    onClick = { onOpenAsset(item.asset.id) },
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = PriceDisplay.assetName(item.asset, settings),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.asset.symbol,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = PriceDisplay.priceNumber(item.asset, item.quote, settings) ?: "—",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val unit = PriceDisplay.unitWord(item.asset, settings)
                            if (unit.isNotEmpty()) {
                                Text(
                                    text = " $unit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        val change = PriceDisplay.changeLabel(item.quote, settings)
                        if (change != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            ChangeBadge(changeText = change, isUp = PriceDisplay.changeIsUp(item.quote))
                        }
                    }
                }
            }
            if (rowItems.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickConverterCard(
    usd: AssetWithQuote,
    state: HomeUiState,
    persian: Boolean,
    onOpenConverter: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_quick_converter),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.home_quick_converter_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = PriceDisplay.priceNumber(usd.asset, usd.quote, state.settings) ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = " " + PriceDisplay.unitWord(usd.asset, state.settings),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            OutlinedButton(onClick = onOpenConverter) {
                Text(stringResource(R.string.home_open_converter))
            }
        }
    }
}

@Composable
private fun WatchlistRow(
    item: AssetWithQuote,
    state: HomeUiState,
    persian: Boolean,
    onOpenAsset: (String) -> Unit,
) {
    val fresh = FreshnessClassifier.classify(
        item.quote,
        System.currentTimeMillis(),
        item.asset.category == ir.cheghadr.core.model.AssetCategory.CRYPTO,
    )
    val staleLabel = when (fresh) {
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
    )
}
