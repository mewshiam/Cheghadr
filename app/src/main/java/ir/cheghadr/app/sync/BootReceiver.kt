package ir.cheghadr.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import ir.cheghadr.core.widgets.WidgetUpdater

/**
 * Restores scheduled work after reboot or app update. WorkManager itself
 * persists periodic requests across reboots; this receiver additionally
 * re-renders every installed widget from the Room cache (glance state can be
 * lost when the process is killed) and nudges one immediate widget update.
 *
 * Android may still defer the next periodic sync beyond its nominal interval —
 * the app never promises minute-precise background updates (see
 * docs/ARCHITECTURE.md, "Refresh policy").
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        // Re-assert the unique periodic work (UPDATE is a no-op when unchanged).
        runCatching {
            WorkManager.getInstance(context).apply {
                // Nothing new to enqueue — periodic work is already persisted;
                // this call only re-initializes after process death.
                pruneWork()
            }
        }

        // Repaint widgets from cached data; no network is performed here.
        WidgetUpdater.refreshAll(context)
    }
}
