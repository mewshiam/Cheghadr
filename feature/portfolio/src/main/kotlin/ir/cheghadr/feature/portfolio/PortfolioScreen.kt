package ir.cheghadr.feature.portfolio

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
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheghadr.core.common.MoneyFormatter
import ir.cheghadr.core.common.PriceDisplay
import ir.cheghadr.core.designsystem.changeDownColor
import ir.cheghadr.core.designsystem.changeUpColor
import ir.cheghadr.core.model.TxSide
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var dialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portfolio_title)) },
                actions = {
                    IconButton(
                        onClick = { viewModel.exportCsv() },
                        enabled = state.transactions.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = stringResource(R.string.portfolio_export),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogOpen = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.portfolio_add))
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
                text = stringResource(R.string.portfolio_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (state.positions.isNotEmpty()) {
                SummaryCard(state)
                Spacer(Modifier.height(12.dp))
            }

            if (state.positions.isEmpty() && state.transactions.isEmpty()) {
                Text(
                    text = stringResource(R.string.portfolio_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.positions, key = { it.asset.asset.id }) { row ->
                        PositionCard(row, state)
                    }
                    items(state.transactions, key = { it.id }) { tx ->
                        TransactionRow(tx, state) { viewModel.delete(tx.id) }
                    }
                }
            }
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.portfolio_add)) },
            text = {
                PortfolioDraftForm(draft, state, viewModel)
            },
            confirmButton = {
                TextButton(onClick = { if (viewModel.saveDraft()) dialogOpen = false }) {
                    Text(stringResource(R.string.portfolio_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(R.string.portfolio_cancel))
                }
            },
        )
    }
}

@Composable
private fun SummaryCard(state: PortfolioUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryRow(stringResource(R.string.portfolio_total_value), state.totalValue?.let {
                MoneyFormatter.formatPrice(it, state.settings.digits)
            } ?: "—")
            SummaryRow(
                stringResource(R.string.portfolio_unrealized),
                state.totalUnrealized?.let { MoneyFormatter.formatChangeAbs(it, state.settings.digits) },
                color = signColor(state.totalUnrealized),
            )
            SummaryRow(
                stringResource(R.string.portfolio_realized),
                MoneyFormatter.formatChangeAbs(state.totalRealized, state.settings.digits),
                color = signColor(state.totalRealized),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String?, color: androidx.compose.ui.graphics.Color? = null) {
    Row {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun signColor(value: BigDecimal?): androidx.compose.ui.graphics.Color =
    when (value?.signum()) {
        1 -> changeUpColor()
        -1 -> changeDownColor()
        else -> MaterialTheme.colorScheme.onSurface
    }

@Composable
private fun PositionCard(row: PositionRow, state: PortfolioUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = PriceDisplay.assetName(row.asset.asset, state.settings),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.currentValue?.let {
                    Text(
                        text = MoneyFormatter.formatPrice(it, state.settings.digits),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.portfolio_quantity_label,
                    MoneyFormatter.formatPrice(row.position.quantity, state.settings.digits),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.position.avgCostPerUnit?.let { avg ->
                Text(
                    text = stringResource(R.string.portfolio_avg_cost) + ": " +
                        MoneyFormatter.formatPrice(avg, state.settings.digits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.unrealized?.let { u ->
                Text(
                    text = stringResource(R.string.portfolio_unrealized) + ": " +
                        MoneyFormatter.formatChangeAbs(u, state.settings.digits),
                    style = MaterialTheme.typography.bodySmall,
                    color = signColor(u),
                )
            } ?: Text(
                text = stringResource(R.string.portfolio_no_price),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun TransactionRow(tx: ir.cheghadr.core.model.Transaction, state: PortfolioUiState, onDelete: () -> Unit) {
    val buy = tx.side == TxSide.BUY
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (if (buy) stringResource(R.string.portfolio_side_buy) else stringResource(R.string.portfolio_side_sell)) +
                    " · " + tx.assetId,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${MoneyFormatter.formatPrice(tx.quantity, state.settings.digits)} × " +
                    MoneyFormatter.formatPrice(tx.pricePerUnit, state.settings.digits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.portfolio_delete),
            )
        }
    }
}

@Composable
private fun PortfolioDraftForm(
    draft: PortfolioViewModel.Draft,
    state: PortfolioUiState,
    viewModel: PortfolioViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.portfolio_asset), style = MaterialTheme.typography.titleSmall)
        if (state.assets.isNotEmpty()) {
            val selected = state.assets.find { it.asset.id == draft.assetId } ?: state.assets.first()
            androidx.compose.runtime.LaunchedEffect(selected) {
                if (draft.assetId.isBlank()) {
                    viewModel.updateDraft { it.copy(assetId = selected.asset.id) }
                }
            }
            val visible = state.assets.take(8)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                visible.take(3).forEach { asset ->
                    FilterChip(
                        selected = asset.asset.id == draft.assetId,
                        onClick = { viewModel.updateDraft { it.copy(assetId = asset.asset.id) } },
                        label = { Text(asset.asset.symbol, maxLines = 1) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                visible.drop(3).take(3).forEach { asset ->
                    FilterChip(
                        selected = asset.asset.id == draft.assetId,
                        onClick = { viewModel.updateDraft { it.copy(assetId = asset.asset.id) } },
                        label = { Text(asset.asset.symbol, maxLines = 1) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.side == TxSide.BUY,
                onClick = { viewModel.updateDraft { it.copy(side = TxSide.BUY) } },
                label = { Text(stringResource(R.string.portfolio_side_buy)) },
            )
            FilterChip(
                selected = draft.side == TxSide.SELL,
                onClick = { viewModel.updateDraft { it.copy(side = TxSide.SELL) } },
                label = { Text(stringResource(R.string.portfolio_side_sell)) },
            )
        }
        OutlinedTextField(
            value = draft.quantity,
            onValueChange = { value -> viewModel.updateDraft { it.copy(quantity = value) } },
            label = { Text(stringResource(R.string.portfolio_quantity)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.price,
            onValueChange = { value -> viewModel.updateDraft { it.copy(price = value) } },
            label = { Text(stringResource(R.string.portfolio_price)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.fee,
            onValueChange = { value -> viewModel.updateDraft { it.copy(fee = value) } },
            label = { Text(stringResource(R.string.portfolio_fee)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.note,
            onValueChange = { value -> viewModel.updateDraft { it.copy(note = value) } },
            label = { Text(stringResource(R.string.portfolio_note)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}
