package ir.cheghadr.core.data.repo

import ir.cheghadr.core.data.catalog.AssetCatalog
import ir.cheghadr.core.data.database.CheghadrDatabase
import ir.cheghadr.core.data.database.SearchHistoryEntity
import ir.cheghadr.core.data.database.toEntity
import ir.cheghadr.core.data.database.toModel
import ir.cheghadr.core.data.provider.ProviderRegistry
import ir.cheghadr.core.data.sync.SyncCoordinator
import ir.cheghadr.core.model.AlertEvaluator
import ir.cheghadr.core.model.AlertRule
import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.PortfolioCalculator
import ir.cheghadr.core.model.ProviderResult
import ir.cheghadr.core.model.Quote
import ir.cheghadr.core.model.Transaction
import ir.cheghadr.core.model.WidgetConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** An asset joined with its latest quote and nothing else hidden. */
data class AssetWithQuote(
    val asset: Asset,
    val quote: Quote?,
)

/** Result of a chart data request. */
sealed interface ChartData {
    /** Real historical series fetched from the asset's provider. */
    data class ProviderSeries(val points: List<HistoryPoint>, val providerId: ir.cheghadr.core.model.ProviderId) : ChartData

    /** Series built from this device's own stored observations. */
    data class LocalSeries(val points: List<HistoryPoint>) : ChartData

    /** Not enough history exists to draw anything. */
    data object Insufficient : ChartData
}

/**
 * Read/write access to market data for the UI layer. All observations are
 * repository-backed Flows: the UI updates after successful refreshes without
 * full-screen reloads, and cached data renders immediately.
 */
@Singleton
class MarketRepository @Inject constructor(
    private val db: CheghadrDatabase,
    private val registry: ProviderRegistry,
    private val sync: SyncCoordinator,
) {
    private val assetDao = db.assetDao()
    private val quoteDao = db.quoteDao()
    private val historyDao = db.historyDao()
    private val searchHistoryDao = db.searchHistoryDao()
    private val providerStatusDao = db.providerStatusDao()

    /** Seeds the catalog into Room on first launch (and after catalog upgrades). */
    suspend fun ensureSeeded() {
        if (assetDao.count() == 0) {
            assetDao.upsertAll(AssetCatalog.all.map { it.toEntity() })
        }
    }

    fun observeCategory(category: AssetCategory): Flow<List<AssetWithQuote>> =
        combine(
            assetDao.observeByCategory(category.name),
            quoteDao.observeAll(),
        ) { assets, quotes ->
            val byAsset = quotes.associateBy { it.assetId }
            assets.map { a ->
                AssetWithQuote(a.toModel(), byAsset[a.id]?.toModel())
            }
        }.distinctUntilChanged()

    /** All currently-available assets with their latest quotes (home dashboard). */
    fun observeAvailable(): Flow<List<AssetWithQuote>> =
        combine(
            assetDao.observeAll(),
            quoteDao.observeAll(),
        ) { assets, quotes ->
            val byAsset = quotes.associateBy { it.assetId }
            assets.filter { it.available }
                .map { a -> AssetWithQuote(a.toModel(), byAsset[a.id]?.toModel()) }
        }.distinctUntilChanged()

    fun observeAsset(assetId: String): Flow<AssetWithQuote?> =
        combine(
            assetDao.observeById(assetId),
            quoteDao.observeById(assetId),
        ) { a, q ->
            if (a == null) null else AssetWithQuote(a.toModel(), q?.toModel())
        }.distinctUntilChanged()

    suspend fun getAsset(assetId: String): Asset? =
        assetDao.getById(assetId)?.toModel()

    /** Cached quote snapshot (no network) — used by widgets and workers. */
    suspend fun getQuote(assetId: String): Quote? =
        quoteDao.getById(assetId)?.toModel()

    fun observeProviderStatuses() =
        providerStatusDao.observeAll()
            .map { list -> list.map { it.toStatus() } }

    private fun ir.cheghadr.core.data.database.ProviderStatusEntity.toStatus() =
        ir.cheghadr.core.model.ProviderStatus(
            providerId = runCatching {
                ir.cheghadr.core.model.ProviderId.valueOf(providerId)
            }.getOrDefault(ir.cheghadr.core.model.ProviderId.COINGECKO),
            enabled = enabled,
            lastSuccessAt = lastSuccessAt,
            lastError = lastError,
            consecutiveFailures = consecutiveFailures,
        )

    /** The most recent successful provider refresh, for the header status chip. */
    fun observeLastSuccessAt(): Flow<Long?> =
        providerStatusDao.observeAll().map { list ->
            list.mapNotNull { it.lastSuccessAt }.maxOrNull()
        }

    /** Manual refresh (pull-to-refresh / foreground entry). */
    suspend fun refresh(): SyncCoordinator.SyncSummary? = sync.refreshNow(manual = true)

    /**
     * Universal search across Persian and English names, tickers and aliases.
     */
    suspend fun search(rawQuery: String): List<AssetWithQuote> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        val normalizedQuery = normalizeFa(query)
        val quotes = quoteDao.getAll().associateBy { it.assetId }
        val results = AssetCatalog.all.map { asset ->
            asset to searchableHaystack(asset)
        }.filter { (_, haystack) ->
            haystack.any { it.contains(normalizedQuery, ignoreCase = true) }
        }.map { (asset, _) ->
            AssetWithQuote(asset, quotes[asset.id]?.toModel())
        }
        return results
    }

    private fun searchableHaystack(asset: Asset): List<String> =
        (asset.aliases + asset.symbol + asset.nameFa + asset.nameEn + asset.id)

    /** Normalizes Persian text variants (ی/ي, ک/ك, ZWNJ) for search. */
    private fun normalizeFa(text: String): String = text
        .replace('\u06CC', 'ی') // Persian yeh → canonical
        .replace('\u064A', 'ی') // Arabic yeh → Persian yeh
        .replace('\u06A9', 'ک') // Arabic keheh
        .replace('\u0643', 'ک') // Arabic kaf
        .replace("\u200C", "") // ZWNJ removed for matching
        .trim()

    fun observeSearchHistory(): Flow<List<String>> =
        searchHistoryDao.observeRecent(10).map { list -> list.map { it.query } }

    suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchHistoryDao.insert(SearchHistoryEntity(trimmed, System.currentTimeMillis()))
    }

    suspend fun clearSearchHistory() = searchHistoryDao.clear()

    /**
     * Chart data for one asset: provider history when supported, otherwise
     * this device's stored observations. Never fabricates points.
     */
    suspend fun chartData(asset: Asset, days: Int): ChartData {
        if (asset.supportsHistory) {
            val provider = registry.byId(asset.providerId)
            if (provider != null) {
                when (val result = provider.history(asset, days)) {
                    is ProviderResult.Success -> {
                        if (result.value.size >= 2) {
                            return ChartData.ProviderSeries(result.value, provider.id)
                        }
                    }
                    is ProviderResult.Failure -> Unit // fall through to local series
                }
            }
        }
        val since = System.currentTimeMillis() - (if (days <= 0) 730L else days.toLong()) * 86_400_000L
        val local = historyDao.getSince(asset.id, since).map { it.toModel() }
        return if (local.size >= 2) ChartData.LocalSeries(local) else ChartData.Insufficient
    }

    /** Recent stored observations for the details screen history table. */
    fun observeLocalHistory(assetId: String, sinceDays: Int = 30): Flow<List<HistoryPoint>> =
        historyDao.observeSince(
            assetId,
            System.currentTimeMillis() - sinceDays * 86_400_000L,
        ).map { list -> list.map { it.toModel() } }
}

/** Favorites / watchlist management. Favorites persist across restarts. */
@Singleton
class FavoritesRepository @Inject constructor(
    private val db: CheghadrDatabase,
) {
    private val favoriteDao = db.favoriteDao()

    fun observeFavorites(): Flow<List<AssetWithQuote>> =
        combine(
            favoriteDao.observeAll(),
            db.assetDao().observeAll(),
            db.quoteDao().observeAll(),
        ) { favorites, assets, quotes ->
            val assetById = assets.associateBy { it.id }
            val quoteById = quotes.associateBy { it.assetId }
            favorites.mapNotNull { f ->
                assetById[f.assetId]?.let { a ->
                    AssetWithQuote(a.toModel(), quoteById[f.assetId]?.toModel())
                }
            }
        }.distinctUntilChanged()

    suspend fun toggle(assetId: String) {
        val existing = favoriteDao.getAll().find { it.assetId == assetId }
        if (existing == null) {
            favoriteDao.insert(
                ir.cheghadr.core.data.database.FavoriteEntity(
                    assetId = assetId,
                    position = (favoriteDao.maxPosition() ?: -1) + 1,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        } else {
            favoriteDao.delete(assetId)
        }
    }

    suspend fun move(assetId: String, up: Boolean) {
        val list = favoriteDao.getAll()
        val index = list.indexOfFirst { it.assetId == assetId }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target !in list.indices) return
        val a = list[index]
        val b = list[target]
        favoriteDao.setPosition(a.assetId, b.position)
        favoriteDao.setPosition(b.assetId, a.position)
    }

    suspend fun clear() = favoriteDao.clear()
}

/** Price alerts: CRUD plus device-side evaluation for the background worker. */
@Singleton
class AlertRepository @Inject constructor(
    private val db: CheghadrDatabase,
) {
    private val alertDao = db.alertDao()

    fun observeRules(): Flow<List<AlertRule>> =
        alertDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun save(rule: AlertRule) {
        alertDao.upsert(rule.toEntity())
    }

    suspend fun delete(id: Long) = alertDao.delete(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val all = alertDao.getAll()
        val target = all.find { it.id == id } ?: return
        alertDao.upsert(target.copy(enabled = enabled))
    }

    /**
     * Evaluates every enabled rule against the latest quotes.
     * Returns the fired notifications and persists updated rule state
     * (duplicate prevention / re-arming).
     */
    suspend fun evaluateAll(now: Long = System.currentTimeMillis()): List<Pair<AlertRule, Quote>> {
        val rules = alertDao.getAll().map { it.toModel() }
        val fired = mutableListOf<Pair<AlertRule, Quote>>()
        for (rule in rules) {
            val quote = db.quoteDao().getById(rule.assetId)?.toModel()
            val outcome = AlertEvaluator.evaluate(rule, quote, now)
            if (outcome.updatedRule != rule) {
                alertDao.upsert(outcome.updatedRule.toEntity())
            }
            if (outcome.shouldNotify && quote != null) {
                fired += outcome.updatedRule to quote
            }
        }
        return fired
    }
}

/** Local-only portfolio transactions and computed positions. */
@Singleton
class PortfolioRepository @Inject constructor(
    private val db: CheghadrDatabase,
) {
    private val portfolioDao = db.portfolioDao()

    fun observeTransactions(): Flow<List<Transaction>> =
        portfolioDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observePositions(): Flow<Map<String, PortfolioCalculator.Position>> =
        portfolioDao.observeAll().map { list ->
            PortfolioCalculator.positions(list.map { it.toModel() })
        }

    suspend fun add(transaction: Transaction) {
        portfolioDao.upsert(transaction.toEntity())
    }

    suspend fun delete(id: Long) = portfolioDao.delete(id)
}

/** Widget configuration persistence, separate from transient UI state. */
@Singleton
class WidgetConfigRepository @Inject constructor(
    private val db: CheghadrDatabase,
) {
    private val dao = db.widgetConfigDao()

    suspend fun get(appWidgetId: Int): WidgetConfig? =
        dao.get(appWidgetId)?.toModel()

    suspend fun getAll(): List<WidgetConfig> =
        dao.getAll().map { it.toModel() }

    suspend fun save(config: WidgetConfig) = dao.upsert(config.toEntity())

    suspend fun delete(appWidgetId: Int) = dao.delete(appWidgetId)

    /** Assets available for widget selection (only quoted/live ones). */
    suspend fun selectableAssets(): List<Asset> =
        db.assetDao().getAvailable().map { it.toModel() }
}
