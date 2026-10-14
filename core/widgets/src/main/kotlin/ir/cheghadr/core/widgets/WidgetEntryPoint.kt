package ir.cheghadr.core.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.appwidget.updateAll
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.FavoritesRepository
import ir.cheghadr.core.data.repo.MarketRepository
import ir.cheghadr.core.data.repo.WidgetConfigRepository
import ir.cheghadr.core.data.sync.SyncCoordinator

/**
 * Hilt entry point granting Glance widgets and action callbacks access to the
 * app graph (widgets are framework-instantiated, not injected).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun marketRepository(): MarketRepository
    fun widgetConfigRepository(): WidgetConfigRepository
    fun favoritesRepository(): FavoritesRepository
    fun settingsRepository(): SettingsRepository
    fun syncCoordinator(): SyncCoordinator
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** Deep-link intent into the app's asset detail screen. */
internal fun openAssetIntent(context: Context, assetId: String?): Intent =
    Intent(ACTION_OPEN_ASSET).apply {
        setPackage(context.packageName)
        data = Uri.parse("cheghadr://asset")
        if (assetId != null) putExtra(EXTRA_ASSET_ID, assetId)
    }

/** Broadcasts the standard widget-update action to all our widget receivers. */
object WidgetUpdater {

    private val receivers = listOf(
        SinglePriceWidgetReceiver::class.java,
        WatchlistWidgetReceiver::class.java,
    )

    /** Triggers recomposition of every installed widget after a data sync. */
    fun refreshAll(context: Context) {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        for (receiver in receivers) {
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, receiver))
            if (ids.isEmpty()) continue
            val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = android.content.ComponentName(context, receiver)
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    /** Glance-native update path (used after configuration changes). */
    suspend fun updateAllWidgets(context: Context) {
        runCatching {
            SinglePriceWidget().updateAll(context)
            WatchlistWidget().updateAll(context)
        }
    }
}

const val ACTION_OPEN_ASSET = "ir.cheghadr.action.OPEN_ASSET"
const val EXTRA_ASSET_ID = "ir.cheghadr.extra.ASSET_ID"
