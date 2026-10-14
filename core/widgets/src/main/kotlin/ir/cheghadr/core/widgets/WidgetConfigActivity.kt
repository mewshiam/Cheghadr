package ir.cheghadr.core.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cheghadr.core.data.repo.WidgetConfigRepository
import ir.cheghadr.core.designsystem.CheghadrTheme
import ir.cheghadr.core.designsystem.MonogramAvatar
import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.WidgetKind
import ir.cheghadr.core.model.WidgetConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuration activity shown when a widget is added (and re-openable from
 * the launcher's widget settings). The selection is persisted per widget id
 * in the widget_configs table — separately from transient UI state.
 */
@AndroidEntryPoint
class WidgetConfigActivity : AppCompatActivity() {

    private val viewModel: WidgetConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        viewModel.load(appWidgetId)

        setContent {
            CheghadrTheme {
                WidgetConfigScreen(
                    viewModel = viewModel,
                    onConfirm = { selected ->
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            viewModel.persist(appWidgetId, selected)
                            WidgetUpdater.refreshAll(applicationContext)
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                            )
                        }
                        finish()
                    },
                )
            }
        }
    }
}

@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val widgetConfigRepository: WidgetConfigRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val assets: List<Asset> = emptyList(),
        val selected: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(appWidgetId: Int) {
        if (_state.value.assets.isNotEmpty()) return
        viewModelScope.launch {
            val config = widgetConfigRepository.get(appWidgetId)
            val assets = widgetConfigRepository.selectableAssets()
            _state.value = UiState(
                loading = false,
                assets = assets,
                selected = config?.assetIds.orEmpty(),
            )
        }
    }

    fun toggle(assetId: String) {
        val current = _state.value
        _state.value = current.copy(
            selected = if (assetId in current.selected) {
                current.selected - assetId
            } else {
                (current.selected + assetId).take(MAX_SELECTION)
            },
        )
    }

    fun persist(appWidgetId: Int, assetIds: List<String>) {
        viewModelScope.launch {
            widgetConfigRepository.save(
                WidgetConfig(
                    appWidgetId = appWidgetId,
                    kind = if (assetIds.size > 1) WidgetKind.ASSET_LIST else WidgetKind.SINGLE_ASSET,
                    assetIds = assetIds,
                ),
            )
        }
    }

    private companion object {
        const val MAX_SELECTION = 5
    }
}

@Composable
private fun WidgetConfigScreen(
    viewModel: WidgetConfigViewModel,
    onConfirm: (List<String>) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.widget_configure_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.widget_select_list_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.widget_configure_search_hint)) },
            singleLine = true,
        )
        if (state.loading) {
            CircularProgressIndicator()
        } else {
            val filtered = state.assets.filter { asset ->
                query.isBlank() ||
                    asset.nameFa.contains(query, ignoreCase = true) ||
                    asset.nameEn.contains(query, ignoreCase = true) ||
                    asset.symbol.contains(query, ignoreCase = true)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.id }) { asset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggle(asset.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonogramAvatar(label = asset.symbol)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(text = asset.nameFa, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = asset.symbol,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(
                            checked = asset.id in state.selected,
                            onCheckedChange = { viewModel.toggle(asset.id) },
                        )
                    }
                }
            }
            Surface {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { onConfirm(state.selected) },
                        enabled = state.selected.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.widget_confirm))
                    }
                }
            }
        }
    }
}
