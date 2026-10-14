package ir.cheghadr.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.cheghadr.app.R
import ir.cheghadr.feature.alerts.AlertsScreen
import ir.cheghadr.feature.assetdetails.AssetDetailScreen
import ir.cheghadr.feature.converter.ConverterScreen
import ir.cheghadr.feature.home.CustomizeDashboardScreen
import ir.cheghadr.feature.home.HomeScreen
import ir.cheghadr.feature.markets.MarketsScreen
import ir.cheghadr.feature.markets.SearchScreen
import ir.cheghadr.feature.portfolio.PortfolioScreen
import ir.cheghadr.feature.settings.SettingsScreen
import ir.cheghadr.feature.watchlist.WatchlistScreen

/** Route names. */
object Routes {
    const val HOME = "home"
    const val MARKETS = "markets"
    const val WATCHLIST = "watchlist"
    const val CONVERTER = "converter"
    const val MORE = "more"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DASHBOARD_EDIT = "dashboard-edit"
    const val ALERTS = "alerts?assetId={assetId}"
    const val PORTFOLIO = "portfolio"
    const val ASSET_DETAIL = "asset/{assetId}"

    fun alerts(assetId: String? = null) = "alerts?assetId=${assetId ?: ""}"
    fun assetDetail(assetId: String) = "asset/$assetId"
}

private data class TopDestination(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topDestinations = listOf(
    TopDestination(Routes.HOME, R.string.nav_home, Icons.Default.Home),
    TopDestination(Routes.MARKETS, R.string.nav_markets, Icons.Default.ShowChart),
    TopDestination(Routes.WATCHLIST, R.string.nav_watchlist, Icons.Default.StarBorder),
    TopDestination(Routes.CONVERTER, R.string.nav_converter, Icons.Default.CurrencyExchange),
    TopDestination(Routes.MORE, R.string.nav_more, Icons.Default.MoreHoriz),
)

/**
 * Root scaffold: native bottom navigation with five primary destinations;
 * everything else lives in the back stack. Deep links from widgets and
 * notifications land on the asset detail screen.
 */
@Composable
fun CheghadrApp(pendingAssetId: kotlinx.coroutines.flow.MutableStateFlow<String?>) {
    val navController = rememberNavController()
    val pending by pendingAssetId.collectAsState()

    // Consume widget/notification deep links.
    LaunchedEffect(pending) {
        pending?.let { assetId ->
            navController.navigate(Routes.assetDetail(assetId))
            pendingAssetId.value = null
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenAsset = { navController.navigate(Routes.assetDetail(it)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenConverter = { navController.navigate(Routes.CONVERTER) },
                    onOpenWatchlist = { navController.navigate(Routes.WATCHLIST) },
                    onOpenCustomize = { navController.navigate(Routes.DASHBOARD_EDIT) },
                )
            }
            composable(Routes.MARKETS) {
                MarketsScreen(
                    onOpenAsset = { navController.navigate(Routes.assetDetail(it)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.WATCHLIST) {
                WatchlistScreen(
                    onOpenAsset = { navController.navigate(Routes.assetDetail(it)) },
                )
            }
            composable(Routes.CONVERTER) {
                ConverterScreen()
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAlerts = { navController.navigate(Routes.alerts()) },
                    onOpenPortfolio = { navController.navigate(Routes.PORTFOLIO) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAsset = { navController.navigate(Routes.assetDetail(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.DASHBOARD_EDIT) {
                CustomizeDashboardScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ALERTS,
                arguments = listOf(navArgument("assetId") { defaultValue = "" }),
            ) {
                AlertsScreen()
            }
            composable(Routes.PORTFOLIO) {
                PortfolioScreen()
            }
            composable(
                route = Routes.ASSET_DETAIL,
                arguments = listOf(navArgument("assetId") { defaultValue = "" }),
            ) {
                AssetDetailScreen(
                    onBack = { navController.popBackStack() },
                    onCreateAlert = { assetId -> navController.navigate(Routes.alerts(assetId)) },
                )
            }
        }
    }
}

/** The "More" hub: alerts, portfolio and settings. */
@Composable
private fun MoreScreen(
    onOpenSettings: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenPortfolio: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.nav_more),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
        MoreRow(
            title = stringResource(R.string.more_alerts),
            subtitle = stringResource(R.string.more_alerts_subtitle),
            onClick = onOpenAlerts,
        )
        MoreRow(
            title = stringResource(R.string.more_portfolio),
            subtitle = stringResource(R.string.more_portfolio_subtitle),
            onClick = onOpenPortfolio,
        )
        MoreRow(
            title = stringResource(R.string.more_settings),
            subtitle = stringResource(R.string.more_settings_subtitle),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
