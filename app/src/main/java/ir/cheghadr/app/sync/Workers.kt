package ir.cheghadr.app.sync

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ir.cheghadr.app.MainActivity
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.AlertRepository
import ir.cheghadr.core.data.repo.MarketRepository
import ir.cheghadr.core.data.sync.SyncCoordinator
import ir.cheghadr.core.notifications.AlertNotifier
import ir.cheghadr.core.widgets.WidgetUpdater
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Periodic data sync: refreshes providers (respecting backoff), then
 * refreshes all installed widgets from the Room cache.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncCoordinator: SyncCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { syncCoordinator.refreshNow(manual = false) }
        WidgetUpdater.refreshAll(applicationContext)
        return Result.success()
    }
}

/**
 * Periodic alert evaluation: refreshes data, evaluates every enabled rule
 * against the latest quotes and posts notifications — honoring the
 * notification permission, the global toggle and quiet hours. Duplicate
 * prevention / re-arming is enforced by the pure AlertEvaluator.
 */
@HiltWorker
class AlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncCoordinator: SyncCoordinator,
    private val alertRepository: AlertRepository,
    private val marketRepository: MarketRepository,
    private val settingsRepository: SettingsRepository,
    private val notifier: AlertNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()

        runCatching { syncCoordinator.refreshNow(manual = false) }
        WidgetUpdater.refreshAll(applicationContext)

        if (!settings.notificationsEnabled) return Result.success()
        if (!notifier.areNotificationsEnabled()) return Result.success()

        val now = System.currentTimeMillis()
        if (isQuietHours(settings.quietHoursStart, settings.quietHoursEnd, now)) {
            return Result.success()
        }

        val fired = alertRepository.evaluateAll(now)
        for ((rule, quote) in fired) {
            val asset = marketRepository.getAsset(rule.assetId) ?: continue
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                putExtra(EXTRA_ASSET_ID, rule.assetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            notifier.notifyAlert(rule, asset, quote, intent, settings.digitStyle)
        }
        return Result.success()
    }

    companion object {
        const val EXTRA_ASSET_ID = "assetId"

        /** Quiet-hours window (minutes of day, supports overnight windows). */
        fun isQuietHours(startMinutes: Int, endMinutes: Int, timeMillis: Long): Boolean {
            if (startMinutes == endMinutes) return false
            val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
            val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            return if (startMinutes < endMinutes) {
                nowMinutes in startMinutes until endMinutes
            } else {
                // Overnight window, e.g. 22:00 → 08:00.
                nowMinutes >= startMinutes || nowMinutes < endMinutes
            }
        }
    }
}
