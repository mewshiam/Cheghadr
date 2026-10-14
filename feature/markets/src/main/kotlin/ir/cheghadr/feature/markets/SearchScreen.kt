package ir.cheghadr.feature.markets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cheghadr.core.common.AppLanguage
import ir.cheghadr.core.common.PriceDisplay
import ir.cheghadr.core.designsystem.EmptyState
import ir.cheghadr.core.designsystem.MarketRow

/** Universal search: Persian/English names, tickers, aliases + history. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }

            if (state.query.length < 2) {
                if (state.history.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.search_history),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.history.forEach { entry ->
                            AssistChip(
                                onClick = { viewModel.onQueryChange(entry) },
                                label = { Text(entry) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.History, contentDescription = null)
                                },
                            )
                        }
                        AssistChip(
                            onClick = viewModel::clearHistory,
                            label = { Text(stringResource(R.string.search_clear_history)) },
                        )
                    }
                }
            } else {
                if (state.results.isEmpty() && !state.searching) {
                    EmptyState(
                        title = stringResource(R.string.search_no_results),
                        message = state.query,
                    )
                } else {
                    LazyColumn {
                        items(state.results, key = { it.asset.id }) { item ->
                            MarketRow(
                                name = PriceDisplay.assetName(item.asset, state.settings),
                                symbol = item.asset.symbol,
                                priceText = PriceDisplay.priceNumber(item.asset, item.quote, state.settings),
                                unitWord = PriceDisplay.unitWord(item.asset, state.settings),
                                changeText = PriceDisplay.changeLabel(item.quote, state.settings),
                                changeUp = PriceDisplay.changeIsUp(item.quote),
                                onClick = { onOpenAsset(item.asset.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
