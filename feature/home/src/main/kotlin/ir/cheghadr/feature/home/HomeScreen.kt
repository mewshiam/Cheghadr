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
import androidx.compose.material.icons.filled.Tune
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
import ir.cheghadr.core.common.DashboardSection
import ir.cheghadr.core.common.TimeFormat
import ir.cheghadr.core.common.PriceDisplay
import ir.cheghadr.core.common.FreshnessClassifier
import ir.cheghadr.core.data.repo.AssetWithQuote
import ir.cheghadr.core.designsystem.ChangeBadge
import ir.cheghadr.core.designsystem.EmptyState
import ir.cheghadr.core.designsystem.MarketRow
import ir.cheghadr.core.designsystem.SectionHeader
import ir.cheghadr.core.designsystem.Sparkline
import ir.cheghadr.core.model.DataFreshness

/**
 * Home dashboard: minimal by default, fully customizable per blueprint §7.
 * Sections render in the user's chosen order and density; mini charts use
 * this device's own recorded observations (labeled, never fabricated).
 */
@Composable
fun HomeScreen(
    onOpenAsset: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenConverter: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenCustomize: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Compact header with app identity, search, customize and refresh.
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
            IconButton(onClick = onOpenCustomize) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.home_customize),
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
            StatusLine(state = state)

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

            state.sections.forEach { section ->
                when (section) {
                    DashboardSection.FEATURED -> FeaturedSection(
                        state = state,
                        onOpenAsset = onOpenAsset,
                    )

                    DashboardSection.QUICK_CONVERTER -> QuickConverterSection(
                        state = state,
                        onOpenConverter = onOpenConverter,
                    )
                    DashboardSection.WATCHLIST_PREVIEW -> WatchlistSection(
                        state = state,
                        onOpenAsset = onOpenAsset,
                        onOpenWatchlist = onOpenWatchlist,
                    )

                    DashboardSection.MARKET_OVERVIEW -> MarketOverviewSection(
                        state = state,
                        onOpenAsset = onOpenAsset,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(state: HomeUiState) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = when {
            state.refreshing -> stringResource(R.string.home_updating)
            state.lastSyncAt != null -> stringResource(
                R.string.home_last_update,
                TimeFormat.formatDateTime(state.lastSyncAt, persian = state.settings.language == AppLanguage.PERSIAN),
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
private fun FeaturedSection(
    state: HomeUiState,
    onOpenAsset: (String) -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.home_featured),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    FeaturedGrid(
        items = state.featured,
        state = state,
        onOpenAsset = onOpenAsset,
    )
}

@Composable
private fun FeaturedGrid(
    items: List<AssetWithQuote>,
    state: HomeUiState,
    onOpenAsset: (String) -> Unit,
) {
    val cardPadding = if (state.compact) 10.dp else 14.dp
    val corner = if (state.compact) 14.dp else 18.dp
    val vertical = if (state.compact) 2.dp else 4.dp

    items.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = vertical),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rowItems.forEach { item ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(corner),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    onClick = { onOpenAsset(item.asset.id) },
                ) {
                    Column(modifier = Modifier.padding(cardPadding)) {
                        Text(
                            text = PriceDisplay.assetName(item.asset, state.settings),
                            style = if (state.compact) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.titleSmall
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.asset.symbol,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(if (state.compact) 4.dp else 8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = PriceDisplay.priceNumber(item.asset, item.quote, state.settings) ?: "—",
                                style = if (state.compact) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.headlineSmall
                                },
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val unit = PriceDisplay.unitWord(item.asset, state.settings)
                            if (unit.isNotEmpty()) {
                                Text(
                                    text = " $unit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        if (state.dashboard.showChanges) {
                            val change = PriceDisplay.changeLabel(item.quote, state.settings)
                            if (change != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                ChangeBadge(
                                    changeText = change,
                                    isUp = PriceDisplay.changeIsUp(item.quote),
                                )
                            }
                        }
                        // Optional mini chart from local observations (§7).
                        if (state.dashboard.showMiniCharts) {
                            state.sparklines[item.asset.id]?.let { points ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Sparkline(
                                    values = points,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (state.compact) 18.dp else 26.dp),
                                )
                            }
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
private fun QuickConverterSection(
    state: HomeUiState,
    onOpenConverter: () -> Unit,
) {
    // Official USD in rial, wherever it currently lives (not just featured).
    val usd = state.usdOfficial ?: return
    if (usd.quote == null) return

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
private fun WatchlistSection(
    state: HomeUiState,
    onOpenAsset: (String) -> Unit,
    onOpenWatchlist: () -> Unit,
) {
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
            WatchlistRow(
                item = item,
                state = state,
                showChanges = state.dashboard.showChanges,
                onOpenAsset = onOpenAsset,
            )
        }
    }
}

/** Market overview (§7 "optional market overview"): top 24h movers, honest. */
@Composable
private fun MarketOverviewSection(
    state: HomeUiState,
    onOpenAsset: (String) -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.home_top_movers),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (state.topGainers.isEmpty() && state.topLosers.isEmpty()) {
        Text(
            text = stringResource(R.string.home_top_movers_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        return
    }
    state.topGainers.forEach { item ->
        MoverRow(item = item, state = state, onOpenAsset = onOpenAsset)
    }
    state.topLosers.forEach { item ->
        if (item.asset.id !in state.topGainers.map { it.asset.id }) {
            MoverRow(item = item, state = state, onOpenAsset = onOpenAsset)
        }
    }
    Text(
        text = stringResource(R.string.home_top_movers_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun MoverRow(
    item: AssetWithQuote,
    state: HomeUiState,
    onOpenAsset: (String) -> Unit,
) {
    MarketRow(
        name = PriceDisplay.assetName(item.asset, state.settings),
        symbol = item.asset.symbol,
        priceText = PriceDisplay.priceNumber(item.asset, item.quote, state.settings),
        unitWord = PriceDisplay.unitWord(item.asset, state.settings),
        changeText = if (state.dashboard.showChanges) {
            PriceDisplay.changeLabel(item.quote, state.settings)
        } else {
            null
        },
        changeUp = PriceDisplay.changeIsUp(item.quote),
        onClick = { onOpenAsset(item.asset.id) },
    )
}

@Composable
private fun WatchlistRow(
    item: AssetWithQuote,
    state: HomeUiState,
    showChanges: Boolean,
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
        changeText = if (showChanges) PriceDisplay.changeLabel(item.quote, state.settings) else null,
        changeUp = PriceDisplay.changeIsUp(item.quote),
        onClick = { onOpenAsset(item.asset.id) },
        staleLabel = staleLabel,
    )
}
