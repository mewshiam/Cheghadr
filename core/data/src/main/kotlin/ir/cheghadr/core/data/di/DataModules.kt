package ir.cheghadr.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import ir.cheghadr.core.data.database.AlertDao
import ir.cheghadr.core.data.database.AssetDao
import ir.cheghadr.core.data.database.CheghadrDatabase
import ir.cheghadr.core.data.database.FavoriteDao
import ir.cheghadr.core.data.database.HistoryDao
import ir.cheghadr.core.data.database.PortfolioDao
import ir.cheghadr.core.data.database.ProviderStatusDao
import ir.cheghadr.core.data.database.QuoteDao
import ir.cheghadr.core.data.database.SearchHistoryDao
import ir.cheghadr.core.data.database.WidgetConfigDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModules {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CheghadrDatabase =
        Room.databaseBuilder(context, CheghadrDatabase::class.java, CheghadrDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideAssetDao(db: CheghadrDatabase): AssetDao = db.assetDao()
    @Provides fun provideQuoteDao(db: CheghadrDatabase): QuoteDao = db.quoteDao()
    @Provides fun provideHistoryDao(db: CheghadrDatabase): HistoryDao = db.historyDao()
    @Provides fun provideFavoriteDao(db: CheghadrDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideAlertDao(db: CheghadrDatabase): AlertDao = db.alertDao()
    @Provides fun provideWidgetConfigDao(db: CheghadrDatabase): WidgetConfigDao = db.widgetConfigDao()
    @Provides fun providePortfolioDao(db: CheghadrDatabase): PortfolioDao = db.portfolioDao()
    @Provides fun provideSearchHistoryDao(db: CheghadrDatabase): SearchHistoryDao = db.searchHistoryDao()
    @Provides fun provideProviderStatusDao(db: CheghadrDatabase): ProviderStatusDao = db.providerStatusDao()

    /**
     * Shared HTTP client for all market-data providers. Timeouts are enforced
     * here once; per-request error mapping happens in the provider layer.
     */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }
}
