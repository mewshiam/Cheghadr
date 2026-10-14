package ir.cheghadr.core.data.sync

import ir.cheghadr.core.data.database.CheghadrDatabase
import ir.cheghadr.core.data.database.HistoryEntity
import ir.cheghadr.core.data.database.ProviderStatusEntity
import ir.cheghadr.core.data.database.toEntity
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.data.provider.QuoteNormalizer
import ir.cheghadr.core.data.provider.ProviderRegistry
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.ProviderResult
import ir.cheghadr.core.model.Quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates data ingestion across providers:
 *  - prevents concurrent duplicate refresh requests (single mutex),
 *  - validates & normalizes every observation (QuoteNormalizer),
 *  - never overwrites valid cached data with malformed responses,
 *  - records provider health and applies bounded exponential backoff,
 *  - samples local history for assets whose provider exposes no history,
 *  - derives daily change from stored observations when providers omit it.
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val db: CheghadrDatabase,
    private val registry: ProviderRegistry,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    data class SyncSummary(
        val startedAt: Long,
        val finishedAt: Long,
        val providersSucceeded: Int,
        val providersFailed: Int,
        val quotesUpdated: Int,
        val errors: List<String>,
    )

    /**
     * Runs one refresh cycle. Returns null when a refresh is already running.
     * [manual] refreshes bypass provider backoff (user-initiated).
     */
    suspend fun refreshNow(manual: Boolean = true): SyncSummary? {
        if (!mutex.tryLock()) return null
        try {
            return withContext(Dispatchers.IO) { doRefresh(manual) }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun doRefresh(manual: Boolean): SyncSummary {
        val startedAt = System.currentTimeMillis()
        val assetDao = db.assetDao()
        val quoteDao = db.quoteDao()
        val historyDao = db.historyDao()
        val statusDao = db.providerStatusDao()

        // TGJU-backed markets become available only when the provider is configured.
        val s = settings.settings.first()
        val tgjuConfigured = s.tgjuEnabled && s.tgjuBaseUrl.isNotBlank()
        assetDao.setAvailableForProvider(ProviderId.TGJU.name, tgjuConfigured)

        val now = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        val quotes = mutableListOf<Quote>()
        val providersSucceeded = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            registry.activeProviders().map { provider ->
                async {
                    val providerId = provider.id.name
                    val prev = statusDao.get(providerId)
                    // Bounded exponential backoff with jitter for periodic syncs.
                    if (!manual && prev != null && prev.consecutiveFailures > 0) {
                        val backoffMs = backoffMillis(prev.consecutiveFailures)
                        val lastAttempt = prev.lastAttemptAt ?: 0L
                        if (now - lastAttempt < backoffMs) {
                            return@async
                        }
                    }
                    statusDao.upsert(
                        (prev ?: ProviderStatusEntity(providerId = providerId, enabled = true)).copy(
                            lastAttemptAt = now,
                        ),
                    )
                    when (val result = provider.latestQuotes()) {
                        is ProviderResult.Success -> {
                            val normalized = result.value.mapNotNull { (assetId, raw) ->
                                val asset = ir.cheghadr.core.data.catalog.AssetCatalog.byId[assetId]
                                    ?: return@mapNotNull null
                                QuoteNormalizer.normalize(asset, raw, System.currentTimeMillis())
                            }
                            synchronized(quotes) { quotes += normalized }
                            providersSucceeded.incrementAndGet()
                            statusDao.upsert(
                                ProviderStatusEntity(
                                    providerId = providerId,
                                    enabled = true,
                                    lastSuccessAt = System.currentTimeMillis(),
                                    lastError = null,
                                    consecutiveFailures = 0,
                                    lastAttemptAt = now,
                                ),
                            )
                        }
                        is ProviderResult.Failure -> {
                            val message = "${provider.id}: ${result.message}"
                            synchronized(errors) { errors += message }
                            statusDao.upsert(
                                (prev ?: ProviderStatusEntity(providerId = providerId, enabled = true)).copy(
                                    lastError = result.message,
                                    consecutiveFailures = prev?.consecutiveFailures?.plus(1) ?: 1,
                                    lastAttemptAt = now,
                                ),
                            )
                        }
                    }
                }
            }.awaitAll()
        }

        // Derive change values from stored observations when the provider omits them,
        // then persist quotes.
        val derived = quotes.map { quote ->
            if (quote.changePercent != null || quote.changeAbs != null) {
                quote
            } else {
                val reference = historyDao.getSince(quote.assetId, startedAt - 30L * 3_600_000L)
                    .firstOrNull()
                    ?.let { BigDecimal(it.price) }
                if (reference != null && reference.signum() > 0) {
                    quote.copy(
                        changeAbs = quote.price.subtract(reference).stripTrailingZeros(),
                        changePercent = QuoteNormalizer.computeChangePercent(quote.price, reference),
                    )
                } else {
                    quote
                }
            }
        }
        if (derived.isNotEmpty()) {
            quoteDao.upsertAll(derived.map { it.toEntity() })

            // Sample local history (max one observation per asset per hour).
            val samples = derived.mapNotNull { q ->
                val last = historyDao.lastSampleTime(q.assetId) ?: 0L
                if (startedAt - last >= 55L * 60_000L) {
                    HistoryEntity(
                        assetId = q.assetId,
                        timestamp = startedAt / 60_000L * 60_000L,
                        price = q.price.toPlainString(),
                    )
                } else {
                    null
                }
            }
            if (samples.isNotEmpty()) historyDao.insertAll(samples)
        }

        // Retention: drop local observations older than two years.
        historyDao.pruneBefore(startedAt - RETENTION_MILLIS)

        val succeeded = quotes.size
        return SyncSummary(
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis(),
            providersSucceeded = providersSucceeded.get(),
            providersFailed = errors.size,
            quotesUpdated = succeeded,
            errors = errors.toList(),
        )
    }

    private fun backoffMillis(consecutiveFailures: Int): Long {
        val capped = consecutiveFailures.coerceIn(1, 6)
        val baseMinutes = (1L shl capped).coerceAtMost(60L) // 2..64 minutes
        val jitter = 0.8 + Math.random() * 0.4
        return (baseMinutes * 60_000L * jitter).toLong()
    }

    private companion object {
        const val RETENTION_MILLIS = 2L * 365 * 24 * 3_600_000L
    }
}
