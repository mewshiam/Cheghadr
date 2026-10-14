package ir.cheghadr.core.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
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
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first

/**
 * Multi-asset watchlist widget (up to 5 configured assets). Each row opens
 * the asset's detail page; data comes from the Room cache.
 */
class WatchlistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = (id as AppWidgetId).appWidgetId
        val entryPoint = context.widgetEntryPoint()
        val market = entryPoint.marketRepository()
        val config = entryPoint.widgetConfigRepository().get(appWidgetId)
        val settings = entryPoint.settingsRepository().settings.first()
        val noDataLabel = context.getString(R.string.widget_no_data)

        val items = config?.assetIds.orEmpty().take(5).mapNotNull { assetId ->
            val asset = market.getAsset(assetId) ?: return@mapNotNull null
            val quote = market.getQuote(assetId)
            WidgetRow(
                name = asset.nameFa,
                price = quote?.let { WidgetSupport.priceWithUnit(context, it, asset.unit, asset.quoteCurrency, settings) },
                change = quote?.let { WidgetSupport.changeLabel(it, settings) },
                changeUp = quote?.changePercent?.signum() ?: 0,
                stale = WidgetSupport.isStale(quote),
                openIntent = openAssetIntent(context, assetId),
            )
        }

        provideContent {
            WatchlistContent(rows = items, fallbackLabel = noDataLabel)
        }
    }

    private data class WidgetRow(
        val name: String,
        val price: String?,
        val change: String?,
        val changeUp: Int,
        val stale: Boolean,
        val openIntent: Intent,
    )

    @Composable
    private fun WatchlistContent(rows: List<WidgetRow>, fallbackLabel: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(day = Color(0xFFF7FAF8), night = Color(0xFF0F1513)))
                .padding(12.dp),
        ) {
            if (rows.isEmpty()) {
                Text(
                    text = fallbackLabel,
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF3F4946), night = Color(0xFFBEC9C4)),
                        fontSize = 13.sp,
                    ),
                )
                return@Column
            }
            rows.forEach { row ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(actionStartActivity(row.openIntent)),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Text(
                        text = row.name,
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF171D1B), night = Color(0xFFDFE4E1)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 1,
                    )
                    Text(
                        text = row.price ?: "—",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF006A60), night = Color(0xFF80D5C3)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (row.change != null) {
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        val up = row.changeUp >= 0
                        Text(
                            text = if (up) "▲${row.change}" else "▼${row.change}",
                            style = TextStyle(
                                color = ColorProvider(
                                    day = if (up) Color(0xFF1B6E3A) else Color(0xFFB3261E),
                                    night = if (up) Color(0xFF7ADB93) else Color(0xFFF2B8B5),
                                ),
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
            }
        }
    }
}
