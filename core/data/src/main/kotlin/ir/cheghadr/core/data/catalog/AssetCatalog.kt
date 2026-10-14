package ir.cheghadr.core.data.catalog

import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.PriceUnit
import ir.cheghadr.core.model.ProviderId

/**
 * The bundled asset catalog: canonical asset definitions with stable IDs and
 * provider mappings. Display names are never used as identifiers.
 *
 * Availability policy (docs/DATA_SOURCES.md):
 *  - Crypto (CoinPaprika), global gold/silver ounce (gold-api.com) and official
 *    FX cross rates (open.er-api / Frankfurter) are live and keyless.
 *  - Iranian free-market USD, domestic gold, coins and Tehran stock indices
 *    require a TGJU-compatible authorized source → shipped unavailable until
 *    the user enables the TGJU provider in Settings.
 */
object AssetCatalog {

    val all: List<Asset> by lazy {
        crypto + currenciesOfficial + currenciesInternational + gold + coins + stocks + commodities
    }

    val byId: Map<String, Asset> by lazy { all.associateBy { it.id } }

    /** Available assets served by a given provider. */
    fun forProvider(providerId: ProviderId): List<Asset> =
        all.filter { it.providerId == providerId && it.available }

    /** TGJU-symbol → canonical asset (used when the TGJU provider is enabled). */
    val byTgjuSymbol: Map<String, Asset> by lazy {
        all.filter { it.providerId == ProviderId.TGJU }.associateBy { it.providerSymbol }
    }

    private fun crypto(
        paprikaId: String,
        symbol: String,
        nameFa: String,
        nameEn: String,
        sort: Int,
    ) = Asset(
        id = "crypto:$symbol",
        category = AssetCategory.CRYPTO,
        symbol = symbol,
        nameFa = nameFa,
        nameEn = nameEn,
        providerId = ProviderId.COIN_PAPRIKA,
        providerSymbol = paprikaId,
        quoteCurrency = "USD",
        unit = PriceUnit.UNIT,
        aliases = listOf(nameFa, nameEn, symbol, paprikaId),
        supportsHistory = true,
        sortOrder = sort,
    )

    private val crypto = listOf(
        crypto("btc-bitcoin", "BTC", "بیت‌کوین", "Bitcoin", 1),
        crypto("eth-ethereum", "ETH", "اتریوم", "Ethereum", 2),
        crypto("usdt-tether", "USDT", "تتر", "Tether", 3),
        crypto("bnb-binance-coin", "BNB", "بایننس کوین", "BNB", 4),
        crypto("sol-solana", "SOL", "سولانا", "Solana", 5),
        crypto("xrp-xrp", "XRP", "ریپل", "XRP", 6),
        crypto("ada-cardano", "ADA", "کاردانو", "Cardano", 7),
        crypto("doge-dogecoin", "DOGE", "دوج‌کوین", "Dogecoin", 8),
        crypto("trx-tron", "TRX", "ترون", "TRON", 9),
        crypto("ton-the-open-network", "TON", "تون‌کوین", "Toncoin", 10),
        crypto("avax-avalanche", "AVAX", "آوالانچ", "Avalanche", 11),
        crypto("link-chainlink", "LINK", "چین‌لینک", "Chainlink", 12),
        crypto("dot-polkadot", "DOT", "پولکادات", "Polkadot", 13),
        crypto("ltc-litecoin", "LTC", "لایت‌کوین", "Litecoin", 14),
        crypto("bch-bitcoin-cash", "BCH", "بیت‌کوین کش", "Bitcoin Cash", 15),
        crypto("shib-shiba-inu", "SHIB", "شیبا اینو", "Shiba Inu", 16),
    )

    /**
     * Official (bank-reference) FX rates quoted in rial, derived from the
     * open.er-api USD base table. Cross rates (EUR, GBP, …) are computed as
     * IRR_per_unit = rates[IRR] / rates[CCY] — explicitly labeled as computed
     * cross rates, never presented as free-market values.
     */
    private fun officialFx(
        symbol: String,
        nameFa: String,
        nameEn: String,
        sort: Int,
    ) = Asset(
        id = "fx:$symbol",
        category = AssetCategory.CURRENCY,
        symbol = symbol,
        nameFa = nameFa,
        nameEn = nameEn,
        providerId = ProviderId.OPEN_ER_API,
        providerSymbol = symbol,
        quoteCurrency = "IRR",
        unit = PriceUnit.RIAL,
        aliases = listOf(nameFa, nameEn, symbol),
        supportsHistory = false, // local observation history only
        sortOrder = sort,
    )

    private val currenciesOfficial = listOf(
        officialFx("USD", "دلار آمریکا (نرخ رسمی)", "US Dollar (official)", 1),
        officialFx("EUR", "یورو (نرخ رسمی)", "Euro (official)", 2),
        officialFx("GBP", "پوند انگلیس (نرخ رسمی)", "British Pound (official)", 3),
        officialFx("AED", "درهم امارات (نرخ رسمی)", "UAE Dirham (official)", 4),
        officialFx("TRY", "لیر ترکیه (نرخ رسمی)", "Turkish Lira (official)", 5),
        officialFx("CNY", "یوان چین (نرخ رسمی)", "Chinese Yuan (official)", 6),
        officialFx("JPY", "ین ژاپن (نرخ رسمی)", "Japanese Yen (official)", 7),
        officialFx("CAD", "دلار کانادا (نرخ رسمی)", "Canadian Dollar (official)", 8),
        officialFx("AUD", "دلار استرالیا (نرخ رسمی)", "Australian Dollar (official)", 9),
        officialFx("CHF", "فرانک سوئیس (نرخ رسمی)", "Swiss Franc (official)", 10),
        // Free-market USD — the flagship Iranian rate — requires an authorized source.
        Asset(
            id = "fx-fm:USD",
            category = AssetCategory.CURRENCY,
            symbol = "USD",
            nameFa = "دلار آمریکا (بازار آزاد)",
            nameEn = "US Dollar (free market)",
            providerId = ProviderId.TGJU,
            providerSymbol = "price_dollar_rl",
            quoteCurrency = "IRR",
            unit = PriceUnit.RIAL,
            aliases = listOf("دلار آزاد", "دلار بازار", "dollar"),
            available = false,
            unavailableReasonFa = "نرخ بازار آزاد نیازمند منبع دارای مجاز (مانند TGJU) است. در تنظیمات می‌توانید منبع مجاز خود را فعال کنید.",
            unavailableReasonEn = "Free-market rates require an authorized source (e.g. TGJU). You can enable your authorized source in Settings.",
            supportsHistory = false,
            sortOrder = 0,
        ),
    )

    /** International cross pairs with real ECB history (Frankfurter). */
    private fun internationalFx(
        symbol: String,
        nameFa: String,
        nameEn: String,
        sort: Int,
    ) = Asset(
        id = "fxint:$symbol-USD",
        category = AssetCategory.CURRENCY,
        symbol = "$symbol/USD",
        nameFa = nameFa,
        nameEn = nameEn,
        providerId = ProviderId.FRANKFURTER,
        providerSymbol = symbol,
        quoteCurrency = symbol,
        unit = PriceUnit.UNIT,
        aliases = listOf(nameFa, nameEn, symbol),
        supportsHistory = true,
        sortOrder = 20 + sort,
    )

    private val currenciesInternational = listOf(
        internationalFx("EUR", "یورو به دلار", "EUR per USD", 1),
        internationalFx("GBP", "پوند به دلار", "GBP per USD", 2),
        internationalFx("CHF", "فرانک به دلار", "CHF per USD", 3),
        internationalFx("JPY", "ین به دلار", "JPY per USD", 4),
    )

    private val gold = listOf(
        tgjuAsset("gold:GRAM18", AssetCategory.GOLD, "طلای ۱۸ عیار", "Gold 18k (per gram)", "geram18", PriceUnit.GRAM, 1),
        tgjuAsset("gold:GRAM24", AssetCategory.GOLD, "طلای ۲۴ عیار", "Gold 24k (per gram)", "geram24", PriceUnit.GRAM, 2),
        tgjuAsset("gold:MESGHAL", AssetCategory.GOLD, "مثقال طلا", "Gold mithqal", "mesghal", PriceUnit.MITHQAL, 3),
        // Global gold ounce — live via gold-api.com (verified 2026-10-14, no key).
        Asset(
            id = "gold:OUNCE",
            category = AssetCategory.GOLD,
            symbol = "XAU",
            nameFa = "انس جهانی طلا",
            nameEn = "Gold ounce (global)",
            providerId = ProviderId.GOLD_API,
            providerSymbol = "XAU",
            quoteCurrency = "USD",
            unit = PriceUnit.OUNCE,
            aliases = listOf("طلا", "انس طلا", "gold", "XAU"),
            available = true,
            supportsHistory = false, // spot only; device-side snapshots build local history
            sortOrder = 4,
        ),
    )

    private val coins = listOf(
        tgjuAsset("coin:EMAMI", AssetCategory.COIN, "سکه امامی", "Emami coin", "sekeemami", PriceUnit.UNIT, 1),
        tgjuAsset("coin:EMAMI_HALF", AssetCategory.COIN, "نیم سکه امامی", "Half Emami coin", "sekeemami1_2", PriceUnit.UNIT, 2),
        tgjuAsset("coin:EMAMI_QUARTER", AssetCategory.COIN, "ربع سکه امامی", "Quarter Emami coin", "sekeemami1_4", PriceUnit.UNIT, 3),
        tgjuAsset("coin:BAHAR", AssetCategory.COIN, "سکه بهار آزادی", "Bahar Azadi coin", "sekebahir", PriceUnit.UNIT, 4),
        tgjuAsset("coin:BAHAR_HALF", AssetCategory.COIN, "نیم سکه بهار آزادی", "Half Bahar Azadi", "sekebahir1_2", PriceUnit.UNIT, 5),
        tgjuAsset("coin:BAHAR_QUARTER", AssetCategory.COIN, "ربع سکه بهار آزادی", "Quarter Bahar Azadi", "sekebahir1_4", PriceUnit.UNIT, 6),
        tgjuAsset("coin:BAHAR_GERAMI", AssetCategory.COIN, "سکه گرمی", "Gerami coin", "sekebahirgerami", PriceUnit.UNIT, 7),
    )

    private val stocks = listOf(
        tgjuAsset("stock:TEDPIX", AssetCategory.STOCK, "شاخص کل بورس تهران", "Tehran SE total index", "shindex", PriceUnit.UNIT, 1, quoteCurrency = "PTS"),
        tgjuAsset("stock:TEDPIX_EQ", AssetCategory.STOCK, "شاخص کل (هم‌وزن)", "Tehran SE equal-weight index", "shindex_e", PriceUnit.UNIT, 2, quoteCurrency = "PTS"),
    )

    private val commodities = listOf(
        // Global silver ounce — live via gold-api.com (verified 2026-10-14, no key).
        Asset(
            id = "commodity:SILVER",
            category = AssetCategory.COMMODITY,
            symbol = "XAG",
            nameFa = "انس نقره",
            nameEn = "Silver ounce",
            providerId = ProviderId.GOLD_API,
            providerSymbol = "XAG",
            quoteCurrency = "USD",
            unit = PriceUnit.OUNCE,
            aliases = listOf("نقره", "silver", "XAG"),
            available = true,
            supportsHistory = false,
            sortOrder = 1,
        ),
        tgjuAsset("commodity:OIL_BRENT", AssetCategory.COMMODITY, "نفت برنت", "Brent oil", "oil_brent", PriceUnit.BARREL, 2, quoteCurrency = "USD"),
    )

    private fun tgjuAsset(
        id: String,
        category: AssetCategory,
        nameFa: String,
        nameEn: String,
        tgjuSymbol: String,
        unit: PriceUnit,
        sort: Int,
        quoteCurrency: String = "IRR",
    ) = Asset(
        id = id,
        category = category,
        symbol = tgjuSymbol,
        nameFa = nameFa,
        nameEn = nameEn,
        providerId = ProviderId.TGJU,
        providerSymbol = tgjuSymbol,
        quoteCurrency = quoteCurrency,
        unit = unit,
        aliases = listOf(nameFa, nameEn, tgjuSymbol),
        available = false,
        unavailableReasonFa = "این بازار در حال حاضر منبع دادهٔ رایگان و مجازی ندارد؛ با فعال‌سازی منبع TGJU دارای مجاز در تنظیمات فعال می‌شود.",
        unavailableReasonEn = "No keyless, authorized source currently covers this market. Enable your authorized TGJU-compatible source in Settings to activate it.",
        supportsHistory = false,
        sortOrder = sort,
    )
}
