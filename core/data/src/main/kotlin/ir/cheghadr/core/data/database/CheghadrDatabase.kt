package ir.cheghadr.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ir.cheghadr.core.model.AlertRule
import ir.cheghadr.core.model.AlertType
import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.PriceUnit
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.Quote
import ir.cheghadr.core.model.Transaction
import ir.cheghadr.core.model.TxSide
import ir.cheghadr.core.model.WidgetConfig
import ir.cheghadr.core.model.WidgetKind
import java.math.BigDecimal

@Database(
    entities = [
        AssetEntity::class,
        QuoteEntity::class,
        HistoryEntity::class,
        FavoriteEntity::class,
        AlertEntity::class,
        WidgetConfigEntity::class,
        TransactionEntity::class,
        SearchHistoryEntity::class,
        ProviderStatusEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CheghadrDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun quoteDao(): QuoteDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun alertDao(): AlertDao
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun providerStatusDao(): ProviderStatusDao

    companion object {
        const val NAME = "cheghadr.db"

        fun build(context: Context): CheghadrDatabase =
            Room.databaseBuilder(context, CheghadrDatabase::class.java, NAME)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}

// ------------------------------------------------------------------ mappers

fun AssetEntity.toModel() = Asset(
    id = id,
    category = runCatching { AssetCategory.valueOf(category) }.getOrDefault(AssetCategory.CRYPTO),
    symbol = symbol,
    nameFa = nameFa,
    nameEn = nameEn,
    providerId = runCatching { ProviderId.valueOf(providerId) }.getOrDefault(ProviderId.COINGECKO),
    providerSymbol = providerSymbol,
    quoteCurrency = quoteCurrency,
    unit = runCatching { PriceUnit.valueOf(unit) }.getOrDefault(PriceUnit.UNIT),
    iconUrl = iconUrl,
    aliases = aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    available = available,
    unavailableReasonFa = unavailableReasonFa,
    unavailableReasonEn = unavailableReasonEn,
    supportsHistory = supportsHistory,
    sortOrder = sortOrder,
)

fun Asset.toEntity() = AssetEntity(
    id = id,
    category = category.name,
    symbol = symbol,
    nameFa = nameFa,
    nameEn = nameEn,
    providerId = providerId.name,
    providerSymbol = providerSymbol,
    quoteCurrency = quoteCurrency,
    unit = unit.name,
    iconUrl = iconUrl,
    aliases = aliases.joinToString(","),
    available = available,
    unavailableReasonFa = unavailableReasonFa,
    unavailableReasonEn = unavailableReasonEn,
    supportsHistory = supportsHistory,
    sortOrder = sortOrder,
)

fun QuoteEntity.toModel() = Quote(
    assetId = assetId,
    price = BigDecimal(price),
    changeAbs = changeAbs?.let { BigDecimal(it) },
    changePercent = changePercent?.let { BigDecimal(it) },
    high24h = high24h?.let { BigDecimal(it) },
    low24h = low24h?.let { BigDecimal(it) },
    volume24h = volume24h?.let { BigDecimal(it) },
    marketCap = marketCap?.let { BigDecimal(it) },
    priceTime = priceTime,
    fetchTime = fetchTime,
    providerId = runCatching { ProviderId.valueOf(providerId) }.getOrDefault(ProviderId.COINGECKO),
)

fun Quote.toEntity() = QuoteEntity(
    assetId = assetId,
    price = price.toPlainString(),
    changeAbs = changeAbs?.toPlainString(),
    changePercent = changePercent?.toPlainString(),
    high24h = high24h?.toPlainString(),
    low24h = low24h?.toPlainString(),
    volume24h = volume24h?.toPlainString(),
    marketCap = marketCap?.toPlainString(),
    priceTime = priceTime,
    fetchTime = fetchTime,
    providerId = providerId.name,
)

fun HistoryEntity.toModel() = HistoryPoint(timestamp = timestamp, price = BigDecimal(price))

fun AlertEntity.toModel() = AlertRule(
    id = id,
    assetId = assetId,
    type = runCatching { AlertType.valueOf(type) }.getOrDefault(AlertType.PRICE_ABOVE),
    threshold = BigDecimal(threshold),
    enabled = enabled,
    cooldownMinutes = cooldownMinutes,
    repeat = repeat,
    allowStaleData = allowStaleData,
    createdAt = createdAt,
    lastTriggeredAt = lastTriggeredAt,
    lastTriggerValue = lastTriggerValue?.let { BigDecimal(it) },
    conditionActive = conditionActive,
)

fun AlertRule.toEntity() = AlertEntity(
    id = id,
    assetId = assetId,
    type = type.name,
    threshold = threshold.toPlainString(),
    enabled = enabled,
    cooldownMinutes = cooldownMinutes,
    repeat = repeat,
    allowStaleData = allowStaleData,
    createdAt = createdAt,
    lastTriggeredAt = lastTriggeredAt,
    lastTriggerValue = lastTriggerValue?.toPlainString(),
    conditionActive = conditionActive,
)

fun TransactionEntity.toModel() = Transaction(
    id = id,
    assetId = assetId,
    side = runCatching { TxSide.valueOf(side) }.getOrDefault(TxSide.BUY),
    quantity = BigDecimal(quantity),
    pricePerUnit = BigDecimal(pricePerUnit),
    fee = BigDecimal(fee),
    timestamp = timestamp,
    note = note,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    assetId = assetId,
    side = side.name,
    quantity = quantity.toPlainString(),
    pricePerUnit = pricePerUnit.toPlainString(),
    fee = fee.toPlainString(),
    timestamp = timestamp,
    note = note,
)

/** Parses a JSON array of asset ids stored in widget configs. */
fun parseWidgetAssetIds(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return json.trim()
        .removePrefix("[")
        .removeSuffix("]")
        .split(',')
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
}

fun widgetAssetIdsJson(ids: List<String>): String = ids.joinToString(",") { "\"$it\"" }.let { "[$it]" }

fun WidgetConfigEntity.toModel() = WidgetConfig(
    appWidgetId = appWidgetId,
    kind = runCatching { WidgetKind.valueOf(kind) }.getOrDefault(WidgetKind.SINGLE_ASSET),
    assetIds = parseWidgetAssetIds(assetIds),
    createdAt = createdAt,
)

fun WidgetConfig.toEntity() = WidgetConfigEntity(
    appWidgetId = appWidgetId,
    kind = kind.name,
    assetIds = widgetAssetIdsJson(assetIds),
    createdAt = createdAt,
)
