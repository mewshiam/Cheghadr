package ir.cheghadr.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ir.cheghadr.app.sync.SyncScheduler
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.repo.MarketRepository
import ir.cheghadr.core.data.sync.SyncCoordinator
import ir.cheghadr.core.notifications.AlertNotifier
import ir.cheghadr.core.widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * «چقدر؟» — application entry point. Wires Hilt, WorkManager, notification
 * channels, the catalog seed, periodic sync scheduling and the default
 * Persian locale.
 */
@HiltAndroidApp
class CheghadrApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var alertNotifier: AlertNotifier
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var marketRepository: MarketRepository
    @Inject lateinit var syncCoordinator: SyncCoordinator

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Persian is the app's default language until the user chooses otherwise.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))
        }

        alertNotifier.ensureChannels()

        appScope.launch {
            marketRepository.ensureSeeded()
            // Initial quiet sync (manual=false → respects provider backoff).
            runCatching { syncCoordinator.refreshNow(manual = false) }
            WidgetUpdater.refreshAll(this@CheghadrApplication)
        }

        // Schedule periodic work; re-schedule when the user changes the interval.
        appScope.launch {
            settingsRepository.settings
                .map { it.refreshIntervalMinutes }
                .distinctUntilChanged()
                .collect { minutes ->
                    syncScheduler.schedulePeriodicSync(minutes)
                    syncScheduler.scheduleAlertChecks()
                }
        }
    }
}
