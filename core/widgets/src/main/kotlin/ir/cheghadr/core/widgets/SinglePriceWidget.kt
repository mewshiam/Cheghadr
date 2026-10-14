package ir.cheghadr.core.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import ir.cheghadr.core.model.Quote

/**
 * Compact single-price home-screen widget. Its configuration (selected asset)
 * is persisted in the widget_configs table, separate from transient UI state.
 * Data comes from the Room cache — never a direct network call; the refresh
 * action delegates to the sync coordinator.
 */
class SinglePriceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = (id as AppWidgetId).appWidgetId
        val entryPoint = context.widgetEntryPoint()
        val config = entryPoint.widgetConfigRepository().get(appWidgetId)
        val assetId = config?.assetIds?.firstOrNull()
        val market = entryPoint.marketRepository()
        val asset = assetId?.let { market.getAsset(it) }
        val quote = assetId?.let { market.getQuote(it) }
        val settings = entryPoint.settingsRepository().settings.first()

        val openIntent = openAssetIntent(context, assetId)
        val noDataLabel = context.getString(R.string.widget_no_data)
        val staleLabel = context.getString(R.string.widget_stale)

        provideContent {
            SinglePriceContent(
                assetName = asset?.nameFa ?: noDataLabel,
                priceText = if (asset != null && quote != null) {
                    WidgetSupport.priceWithUnit(context, quote, asset.unit, asset.quoteCurrency, settings)
                } else {
                    null
                },
                changeText = quote?.let { WidgetSupport.changeLabel(it, settings) },
                changeUp = quote?.changePercent?.signum(),
                stale = WidgetSupport.isStale(quote),
                staleLabel = staleLabel,
                openIntent = openIntent,
            )
        }
    }

    @Composable
    private fun SinglePriceContent(
        assetName: String,
        priceText: String?,
        changeText: String?,
        changeUp: Int?,
        stale: Boolean,
        staleLabel: String,
        openIntent: Intent,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(day = Color(0xFFF7FAF8), night = Color(0xFF0F1513)))
                .padding(12.dp)
                .clickable(actionStartActivity(openIntent)),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = assetName,
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF171D1B), night = Color(0xFFDFE4E1)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = "↻",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF006A60), night = Color(0xFF80D5C3)),
                        fontSize = 15.sp,
                    ),
                    modifier = GlanceModifier
                        .padding(start = 8.dp)
                        .clickable(actionRunCallback<RefreshAction>()),
                )
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = priceText ?: "—",
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF006A60), night = Color(0xFF80D5C3)),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            val change = changeText
            if (change != null) {
                val up = (changeUp ?: 0) >= 0
                Text(
                    text = if (up) "▲ $change" else "▼ $change",
                    style = TextStyle(
                        color = ColorProvider(
                            day = if (up) Color(0xFF1B6E3A) else Color(0xFFB3261E),
                            night = if (up) Color(0xFF7ADB93) else Color(0xFFF2B8B5),
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            if (stale) {
                Text(
                    text = staleLabel,
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF815512), night = Color(0xFFF6BE62)),
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}

/** Shared refresh callback: syncs data and refreshes every installed widget. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        context.widgetEntryPoint().syncCoordinator().refreshNow(manual = true)
        WidgetUpdater.refreshAll(context)
    }
}

/** Quote snapshot accessor used by widgets (cached data only). */
internal suspend fun ir.cheghadr.core.data.repo.MarketRepository.quoteOrNull(assetId: String): Quote? =
    getQuote(assetId)
