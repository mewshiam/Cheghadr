package ir.cheghadr.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.cheghadr.app.navigation.CheghadrApp
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.preferences.UserSettings
import ir.cheghadr.core.designsystem.CheghadrTheme
import ir.cheghadr.core.widgets.ACTION_OPEN_ASSET
import ir.cheghadr.core.widgets.EXTRA_ASSET_ID
import ir.cheghadr.core.widgets.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

/**
 * Single-activity Compose entry point. Handles widget/notification deep links
 * (ir.cheghadr.action.OPEN_ASSET / assetId extra) and applies the persisted
 * theme + language preferences.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    /** Pending deep-link asset id, consumed by the navigation host. */
    val pendingAssetId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        pendingAssetId.value = extractAssetId(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
            CheghadrTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColors,
            ) {
                CheghadrApp(pendingAssetId = pendingAssetId)
            }
        }

        // Refresh widgets when the user returns to the app (cached data → widget).
        lifecycleScope.launch {
            WidgetUpdater.refreshAll(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractAssetId(intent)?.let { pendingAssetId.value = it }
    }

    private fun extractAssetId(intent: Intent?): String? {
        if (intent == null) return null
        val relevant = intent.action == ACTION_OPEN_ASSET ||
            intent.hasExtra(EXTRA_ASSET_ID) ||
            intent.hasExtra(ir.cheghadr.app.sync.AlertWorker.EXTRA_ASSET_ID)
        if (!relevant) return null
        return intent.getStringExtra(EXTRA_ASSET_ID)
            ?: intent.getStringExtra(ir.cheghadr.app.sync.AlertWorker.EXTRA_ASSET_ID)
    }
}
