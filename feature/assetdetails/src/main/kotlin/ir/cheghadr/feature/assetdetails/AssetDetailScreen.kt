package ir.cheghadr.feature.assetdetails

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.PriceDisplay
import ir.cheghadr.core.common.TimeFormat
import ir.cheghadr.core.data.repo.ChartData
import ir.cheghadr.core.designsystem.ChangeBadge
import ir.cheghadr.core.designsystem.EmptyState
import ir.cheghadr.core.designsystem.StaleChip
import ir.cheghadr.core.model.HistoryRange
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Asset detail page: quote with source attribution and freshness, interactive
 * historical chart with range selection, statistics, watchlist toggle,
 * alert creation and sharing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    onBack: () -> Unit,
    onCreateAlert: (String) -> Unit,
    viewModel: AssetDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item = state.asset

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item?.let { PriceDisplay.assetName(it.asset, state.settings) } ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        if (state.isFavorite) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(R.string.detail_favorite_remove),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.detail_favorite_add),
                            )
                        }
                    }
                    IconButton(onClick = { onCreateAlert(viewModel.assetId) }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.detail_add_alert),
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            viewModel.shareText()?.let { text ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.detail_share),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (item == null) {
            EmptyState(title = stringResource(R.string.detail_unavailable_title), message = "")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val asset = item.asset
            val quote = item.quote

            if (!asset.available || quote == null) {
                EmptyState(
                    title = stringResource(R.string.detail_unavailable_title),
                    message = if (state.settings.language == AppLanguage.PERSIAN) {
                        asset.unavailableReasonFa ?: ""
                    } else {
                        asset.unavailableReasonEn ?: ""
                    },
                )
                return@Column
            }

            // Header quote block.
            Text(
                text = asset.symbol,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(
                    text = PriceDisplay.priceNumber(asset, quote, state.settings) ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val unit = PriceDisplay.unitWord(asset, state.settings)
                if (unit.isNotEmpty()) {
                    Text(
                        text = " $unit",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PriceDisplay.changeLabel(quote, state.settings)?.let {
                    ChangeBadge(changeText = it, isUp = PriceDisplay.changeIsUp(quote))
                }
                quote.changeAbs?.let {
                    Text(
                        text = ir.cheghadr.core.common.MoneyFormatter.formatChangeAbs(
                            it,
                            state.settings.digits,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.detail_source,
                    providerLabel(asset.providerId, state.settings),
                ) + " · " + stringResource(
                    R.string.detail_last_update,
                    TimeFormat.formatDateTime(quote.priceTime ?: quote.fetchTime, state.settings.language == AppLanguage.PERSIAN),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Statistics where genuinely available (crypto spot fields).
            val stats = buildList {
                quote.high24h?.let { add(stringResource(R.string.detail_stats_24h_high) to formatStat(it, state.settings)) }
                quote.low24h?.let { add(stringResource(R.string.detail_stats_24h_low) to formatStat(it, state.settings)) }
                quote.volume24h?.let { add(stringResource(R.string.detail_stats_volume) to formatStat(it, state.settings)) }
                quote.marketCap?.let { add(stringResource(R.string.detail_stats_market_cap) to formatStat(it, state.settings)) }
            }
            if (stats.isNotEmpty()) {
                StatsGrid(stats)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Range selection.
            Text(
                text = stringResource(R.string.detail_history_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                HistoryRange.entries.forEach { r ->
                    AssistChip(
                        onClick = { viewModel.selectRange(r) },
                        label = { Text(rangeLabel(r)) },
                        colors = if (state.range == r) {
                            androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        } else {
                            androidx.compose.material3.AssistChipDefaults.assistChipColors()
                        },
                    )
                }
            }

            // Chart with honest source labeling.
            when (val chart = state.chart.data) {
                is ChartData.ProviderSeries -> {
                    LineChart(
                        points = chart.points,
                        priceFormatter = { formatStat(it, state.settings) },
                        timeFormatter = { formatChartTime(it, state.settings) },
                    )
                    Text(
                        text = stringResource(R.string.detail_history_provider, providerLabel(chart.providerId, state.settings)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                is ChartData.LocalSeries -> {
                    LineChart(
                        points = chart.points,
                        priceFormatter = { formatStat(it, state.settings) },
                        timeFormatter = { formatChartTime(it, state.settings) },
                    )
                    Text(
                        text = stringResource(R.string.detail_history_local),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                ChartData.Insufficient -> {
                    if (state.chart.loading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        EmptyState(
                            title = stringResource(R.string.detail_history_insufficient),
                            message = stringResource(R.string.detail_history_local),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatsGrid(stats: List<Pair<String, String>>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stats.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(text = value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatStat(value: java.math.BigDecimal, state: ir.cheghadr.core.common.DisplaySettings): String =
    ir.cheghadr.core.common.MoneyFormatter.formatPrice(value, state.digits)

private fun formatChartTime(timestamp: Long, state: ir.cheghadr.core.common.DisplaySettings): String {
    val dateTime = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
    return if (state.language == AppLanguage.PERSIAN) {
        val j = ir.cheghadr.core.common.JalaliCalendar.toJalali(dateTime.toLocalDate())
        ir.cheghadr.core.common.MoneyFormatter.localizeDigitsAndSeparators(
            "${j.month}/${j.day}",
            ir.cheghadr.core.common.DigitStyle.PERSIAN,
        )
    } else {
        "${dateTime.monthValue}/${dateTime.dayOfMonth}"
    }
}

@Composable
private fun providerLabel(providerId: ir.cheghadr.core.model.ProviderId, settings: ir.cheghadr.core.common.DisplaySettings): String =
    when (providerId) {
        ir.cheghadr.core.model.ProviderId.COIN_PAPRIKA -> "CoinPaprika"
        ir.cheghadr.core.model.ProviderId.COINGECKO -> "CoinGecko"
        ir.cheghadr.core.model.ProviderId.OPEN_ER_API -> if (settings.language == AppLanguage.PERSIAN) "ExchangeRate-API (نرخ رسمی)" else "ExchangeRate-API (official)"
        ir.cheghadr.core.model.ProviderId.FRANKFURTER -> "Frankfurter (ECB)"
        ir.cheghadr.core.model.ProviderId.GOLD_API -> if (settings.language == AppLanguage.PERSIAN) "gold-api.com (دلاری)" else "gold-api.com (USD)"
        ir.cheghadr.core.model.ProviderId.TGJU -> if (settings.language == AppLanguage.PERSIAN) "منبع TGJU شما" else "your TGJU source"
        ir.cheghadr.core.model.ProviderId.APP -> if (settings.language == AppLanguage.PERSIAN) "محاسبهٔ محلی" else "local computation"
    }

@Composable
private fun rangeLabel(range: HistoryRange): String = when (range) {
    HistoryRange.DAY_1 -> stringResource(R.string.detail_range_1d)
    HistoryRange.DAY_7 -> stringResource(R.string.detail_range_7d)
    HistoryRange.DAY_30 -> stringResource(R.string.detail_range_30d)
    HistoryRange.DAY_90 -> stringResource(R.string.detail_range_90d)
    HistoryRange.DAY_180 -> stringResource(R.string.detail_range_180d)
    HistoryRange.YEAR_1 -> stringResource(R.string.detail_range_1y)
    HistoryRange.YEAR_5 -> stringResource(R.string.detail_range_5y)
    HistoryRange.MAX -> stringResource(R.string.detail_range_max)
}
