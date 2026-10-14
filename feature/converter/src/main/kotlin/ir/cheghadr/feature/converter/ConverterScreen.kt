package ir.cheghadr.feature.converter

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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.MoneyFormatter
import ir.cheghadr.core.common.PriceDisplay
import ir.cheghadr.core.common.TimeFormat
import ir.cheghadr.core.data.repo.AssetWithQuote
import java.math.BigDecimal

/**
 * Currency and gold calculator. Offline conversions use cached rates with a
 * visible staleness warning; retail gold estimates show a full breakdown and
 * are labeled as estimates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.converter_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = state.tab) {
                Tab(
                    selected = state.tab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text(stringResource(R.string.converter_tab_currency)) },
                )
                Tab(
                    selected = state.tab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text(stringResource(R.string.converter_tab_gold)) },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (state.tab == 0) {
                    CurrencyTab(state, viewModel)
                } else {
                    GoldTab(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun CurrencyTab(state: ConverterUiState, viewModel: ConverterViewModel) {
    val from = state.currency.assets.find { it.asset.id == state.currency.fromId }
    val to = state.currency.assets.find { it.asset.id == state.currency.toId }
    val result = viewModel.conversionResult(state)

    OutlinedTextField(
        value = state.currency.amountInput,
        onValueChange = viewModel::onAmountChange,
        label = { Text(stringResource(R.string.converter_amount)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = result == null && state.currency.amountInput.isNotBlank(),
        supportingText = if (result == null && state.currency.amountInput.isNotBlank()) {
            { Text(stringResource(R.string.converter_invalid_amount)) }
        } else {
            null
        },
    )
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CurrencyPicker(
            label = stringResource(R.string.converter_from),
            selected = from,
            options = state.currency.assets,
            settings = state.currency.settings,
            modifier = Modifier.weight(1f),
            onSelect = viewModel::onFromChange,
        )
        IconButton(onClick = viewModel::swap) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = stringResource(R.string.converter_swap),
            )
        }
        CurrencyPicker(
            label = stringResource(R.string.converter_to),
            selected = to,
            options = state.currency.assets,
            settings = state.currency.settings,
            modifier = Modifier.weight(1f),
            onSelect = viewModel::onToChange,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.converter_result),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = result?.let { MoneyFormatter.formatPrice(it, state.currency.settings.digits) } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (to != null && result != null) {
                Text(
                    text = PriceDisplay.unitWord(to.asset, state.currency.settings),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (from != null && to != null && from.quote != null && to.quote != null) {
                val usdIrr = state.currency.assets.find { it.asset.id == "fx:USD" }?.quote
                val rate = CurrencyConverter.convert(BigDecimal.ONE, from.asset to from.quote, to.asset to to.quote, usdIrr)
                if (rate != null) {
                    Text(
                        text = stringResource(
                            R.string.converter_rate,
                            from.asset.symbol,
                            MoneyFormatter.formatPrice(rate, state.currency.settings.digits),
                            PriceDisplay.unitWord(to.asset, state.currency.settings),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.converter_cross_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.currency.stale && state.currency.lastUpdate != null) {
        Text(
            text = stringResource(
                R.string.converter_offline_warning,
                TimeFormat.formatDateTime(state.currency.lastUpdate, state.currency.settings.language == AppLanguage.PERSIAN),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun CurrencyPicker(
    label: String,
    selected: AssetWithQuote?,
    options: List<AssetWithQuote>,
    settings: ir.cheghadr.core.common.DisplaySettings,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        androidx.compose.material3.OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected?.let { PriceDisplay.assetName(it.asset, settings) } ?: "—",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(PriceDisplay.assetName(option.asset, settings), maxLines = 1)
                            Text(
                                option.asset.symbol,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(option.asset.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun GoldTab(state: ConverterUiState, viewModel: ConverterViewModel) {
    val gram18 = runCatching { BigDecimal(state.gold.gram18Input.replace(',', '.')) }.getOrNull()
    val digits = state.gold.settings.digits

    OutlinedTextField(
        value = state.gold.gram18Input,
        onValueChange = viewModel::onGram18Change,
        label = { Text(stringResource(R.string.converter_gold_gram18)) },
        supportingText = { Text(stringResource(R.string.converter_gold_prefill_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (gram18 != null && gram18.signum() > 0) {
        // Show all values in the display currency (toman by default).
        val toman = state.gold.settings.displayToman
        val unitWord = if (toman) "تومان" else "ریال"
        val g18 = if (toman) gram18.divide(BigDecimal.TEN) else gram18
        val mithqal18 = GoldCalculator.mithqalToGrams(BigDecimal.ONE).multiply(g18)
            .setScale(0, java.math.RoundingMode.HALF_UP)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GoldRow(
                    label = stringResource(R.string.converter_gold_gram24),
                    value = GoldCalculator.convertPurity(g18, 18, 24),
                    digits = digits,
                    unit = unitWord,
                )
                GoldRow(
                    label = stringResource(R.string.converter_gold_mithqal) + " (۱۸)",
                    value = mithqal18,
                    digits = digits,
                    unit = unitWord,
                )
                GoldRow(
                    label = stringResource(R.string.converter_gold_ounce),
                    value = GoldCalculator.ounceToGrams(BigDecimal.ONE)
                        .multiply(GoldCalculator.convertPurity(g18, 18, 24))
                        .setScale(0, java.math.RoundingMode.HALF_UP),
                    digits = digits,
                    unit = unitWord,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.converter_gold_coins),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GoldCalculator.COINS.forEach { coin ->
                    GoldRow(
                        label = coin.nameFa,
                        value = GoldCalculator.coinIntrinsicValue(coin, gram18).let {
                            if (toman) it.divide(BigDecimal.TEN) else it
                        },
                        digits = digits,
                        unit = unitWord,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.converter_gold_breakdown),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            PercentField(
                label = stringResource(R.string.converter_gold_making),
                value = state.gold.makingInput,
                onValueChange = viewModel::onMakingChange,
                modifier = Modifier.weight(1f),
            )
            PercentField(
                label = stringResource(R.string.converter_gold_tax),
                value = state.gold.taxInput,
                onValueChange = viewModel::onTaxChange,
                modifier = Modifier.weight(1f),
            )
            PercentField(
                label = stringResource(R.string.converter_gold_discount),
                value = state.gold.discountInput,
                onValueChange = viewModel::onDiscountChange,
                modifier = Modifier.weight(1f),
            )
        }
        val base = mithqal18
        val breakdown = GoldCalculator.retailBreakdown(
            base = base,
            makingChargePercent = state.gold.makingInput.toPercent(),
            taxPercent = state.gold.taxInput.toPercent(),
            discountPercent = state.gold.discountInput.toPercent(),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BreakdownRow(stringResource(R.string.converter_gold_breakdown_base), breakdown.base, digits, unitWord)
                BreakdownRow(stringResource(R.string.converter_gold_breakdown_making), breakdown.makingCharge, digits, unitWord)
                BreakdownRow(stringResource(R.string.converter_gold_breakdown_tax), breakdown.tax, digits, unitWord)
                BreakdownRow(stringResource(R.string.converter_gold_breakdown_discount), "−" + MoneyFormatter.formatPrice(breakdown.discount, digits), digits, unitWord)
                Text(
                    text = stringResource(R.string.converter_gold_breakdown_total) + ": " +
                        MoneyFormatter.formatPrice(breakdown.total, digits) + " " + unitWord,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Text(
            text = stringResource(R.string.converter_gold_estimate_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

private fun String.toPercent(): BigDecimal =
    runCatching { BigDecimal(replace(',', '.')) }.getOrDefault(BigDecimal.ZERO).min(BigDecimal.valueOf(100))

@Composable
private fun GoldRow(label: String, value: BigDecimal, digits: ir.cheghadr.core.common.DigitStyle, unit: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = MoneyFormatter.formatPrice(value, digits) + " " + unit,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BreakdownRow(label: String, value: BigDecimal, digits: ir.cheghadr.core.common.DigitStyle, unit: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(
            text = MoneyFormatter.formatPrice(value, digits) + " " + unit,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BreakdownRow(label: String, formatted: String, digits: ir.cheghadr.core.common.DigitStyle, unit: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(text = formatted + " " + unit, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PercentField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        singleLine = true,
    )
}
