package ir.cheghadr.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Upsert
    suspend fun upsertAll(assets: List<AssetEntity>)

    @Query("SELECT * FROM assets ORDER BY sortOrder")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE category = :category ORDER BY sortOrder")
    fun observeByCategory(category: String): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    fun observeById(id: String): Flow<AssetEntity?>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getById(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE available = 1 ORDER BY sortOrder")
    suspend fun getAvailable(): List<AssetEntity>

    @Query("SELECT COUNT(*) FROM assets")
    suspend fun count(): Int

    @Query("UPDATE assets SET available = :available WHERE providerId = :providerId")
    suspend fun setAvailableForProvider(providerId: String, available: Boolean)
}

@Dao
interface QuoteDao {
    @Upsert
    suspend fun upsertAll(quotes: List<QuoteEntity>)

    @Upsert
    suspend fun upsert(quote: QuoteEntity)

    @Query("SELECT * FROM quotes")
    fun observeAll(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE assetId = :assetId")
    fun observeById(assetId: String): Flow<QuoteEntity?>

    @Query("SELECT * FROM quotes WHERE assetId = :assetId")
    suspend fun getById(assetId: String): QuoteEntity?

    @Query("SELECT * FROM quotes")
    suspend fun getAll(): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE assetId IN (:assetIds)")
    suspend fun getByIds(assetIds: List<String>): List<QuoteEntity>
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<HistoryEntity>)

    @Query("SELECT * FROM history WHERE assetId = :assetId AND timestamp >= :since ORDER BY timestamp")
    fun observeSince(assetId: String, since: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE assetId = :assetId AND timestamp >= :since ORDER BY timestamp")
    suspend fun getSince(assetId: String, since: Long): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE assetId IN (:assetIds) AND timestamp >= :since ORDER BY timestamp")
    suspend fun getSinceForIds(assetIds: List<String>, since: Long): List<HistoryEntity>

    @Query("SELECT MAX(timestamp) FROM history WHERE assetId = :assetId")
    suspend fun lastSampleTime(assetId: String): Long?

    @Query("DELETE FROM history WHERE timestamp < :before")
    suspend fun pruneBefore(before: Long)

    @Query("SELECT COUNT(*) FROM history WHERE assetId = :assetId")
    suspend fun countFor(assetId: String): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY position")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY position")
    suspend fun getAll(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE assetId = :assetId")
    suspend fun delete(assetId: String)

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Query("SELECT MAX(position) FROM favorites")
    suspend fun maxPosition(): Int?

    @Query("UPDATE favorites SET position = :position WHERE assetId = :assetId")
    suspend fun setPosition(assetId: String, position: Int)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts")
    suspend fun getAll(): List<AlertEntity>

    @Upsert
    suspend fun upsert(alert: AlertEntity): Long

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM alerts WHERE assetId = :assetId")
    suspend fun deleteForAsset(assetId: String)
}

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :appWidgetId")
    suspend fun get(appWidgetId: Int): WidgetConfigEntity?

    @Query("SELECT * FROM widget_configs")
    suspend fun getAll(): List<WidgetConfigEntity>

    @Upsert
    suspend fun upsert(config: WidgetConfigEntity)

    @Query("DELETE FROM widget_configs WHERE appWidgetId = :appWidgetId")
    suspend fun delete(appWidgetId: Int)
}

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolio_transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM portfolio_transactions ORDER BY timestamp ASC")
    suspend fun getAll(): List<TransactionEntity>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity): Long

    @Query("DELETE FROM portfolio_transactions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface ProviderStatusDao {
    @Query("SELECT * FROM provider_status")
    fun observeAll(): Flow<List<ProviderStatusEntity>>

    @Query("SELECT * FROM provider_status WHERE providerId = :providerId")
    suspend fun get(providerId: String): ProviderStatusEntity?

    @Upsert
    suspend fun upsert(status: ProviderStatusEntity)
}
