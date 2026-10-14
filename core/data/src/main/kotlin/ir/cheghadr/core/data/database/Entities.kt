package ir.cheghadr.core.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Persisted asset metadata; the catalog is re-seeded on version upgrades. */
@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val category: String,
    val symbol: String,
    val nameFa: String,
    val nameEn: String,
    val providerId: String,
    val providerSymbol: String,
    val quoteCurrency: String,
    val unit: String,
    val iconUrl: String? = null,
    /** Comma-separated search aliases. */
    val aliases: String = "",
    val available: Boolean = true,
    val unavailableReasonFa: String? = null,
    val unavailableReasonEn: String? = null,
    val supportsHistory: Boolean = false,
    val sortOrder: Int = 0,
)

/** Latest quote per asset; monetary values stored as decimal strings. */
@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey val assetId: String,
    val price: String,
    val changeAbs: String? = null,
    val changePercent: String? = null,
    val high24h: String? = null,
    val low24h: String? = null,
    val volume24h: String? = null,
    val marketCap: String? = null,
    val priceTime: Long? = null,
    val fetchTime: Long,
    val providerId: String,
)

/** Locally recorded observations; used for charts when provider history is absent. */
@Entity(tableName = "history", primaryKeys = ["assetId", "timestamp"])
data class HistoryEntity(
    val assetId: String,
    val timestamp: Long,
    val price: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val assetId: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val assetId: String,
    val type: String,
    val threshold: String,
    val enabled: Boolean = true,
    val cooldownMinutes: Int = 60,
    val repeat: Boolean = false,
    val allowStaleData: Boolean = false,
    val createdAt: Long,
    val lastTriggeredAt: Long? = null,
    val lastTriggerValue: String? = null,
    val conditionActive: Boolean = false,
)

@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey val appWidgetId: Int,
    val kind: String,
    /** JSON array of canonical asset ids. */
    val assetIds: String,
    val createdAt: Long,
)

@Entity(tableName = "portfolio_transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val assetId: String,
    val side: String,
    val quantity: String,
    val pricePerUnit: String,
    val fee: String,
    val timestamp: Long,
    val note: String? = null,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

/** Provider health, exposed in settings and used for backoff decisions. */
@Entity(tableName = "provider_status")
data class ProviderStatusEntity(
    @PrimaryKey val providerId: String,
    val enabled: Boolean = true,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
    val consecutiveFailures: Int = 0,
    val lastAttemptAt: Long? = null,
)
