import type { Asset, AssetCategory } from "./types";

/**
 * «چقدر؟» asset catalog — static metadata for every supported market.
 *
 * HONESTY NOTE (blueprint §3): crypto assets map to CoinGecko ids and are quoted
 * LIVE in USD. All Iranian-market assets (currency/gold/coin/stock) currently come
 * from the clearly-labeled DEMO engine — `basePrice` is a plausible simulation
 * anchor, never a real market quote. A real provider (e.g. authorized TGJU) can
 * be plugged in without touching the UI (see README / DATA_SOURCES).
 */

type Row = [
  id: string,
  symbol: string,
  nameFa: string,
  nameEn: string,
  basePrice: number,
  decimals: number,
  badge: string,
  vol: number,
  aliases: string[],
  extra?: Partial<Asset>,
];

const IRT = "IRT"; // Iranian toman
const IRR = "IRR"; // Iranian rial (TSE convention)
const USD = "USD";

function make(category: AssetCategory, rows: Row[]): Asset[] {
  return rows.map(
    ([id, symbol, nameFa, nameEn, basePrice, decimals, badge, vol, aliases, extra]) => ({
      id,
      category,
      symbol,
      nameFa,
      nameEn,
      quoteCurrency:
        category === "crypto"
          ? USD
          : category === "stock"
            ? IRR
            : (extra?.quoteCurrency ?? IRT),
      unitFa: extra?.unitFa ?? (category === "stock" ? "سهم" : "عدد"),
      unitEn: extra?.unitEn ?? (category === "stock" ? "share" : "each"),
      decimals,
      aliases,
      badge,
      vol,
      basePrice,
      ...extra,
    }),
  );
}

const CURRENCIES = make("currency", [
  ["usd-irt", "USD/IRT", "دلار آمریکا", "US Dollar", 104_250, 0, "💵", 0.004, ["dollar", "دلار", "usd", "یزدی", "اسکناس دلار"]],
  ["eur-irt", "EUR/IRT", "یورو", "Euro", 113_400, 0, "💶", 0.004, ["euro", "یورو", "eur"]],
  ["gbp-irt", "GBP/IRT", "پوند انگلیس", "British Pound", 132_800, 0, "💷", 0.004, ["pound", "پوند", "gbp"]],
  ["aed-irt", "AED/IRT", "درهم امارات", "UAE Dirham", 28_380, 0, "🇦🇪", 0.003, ["dirham", "درهم", "aed"]],
  ["try-irt", "TRY/IRT", "لیر ترکیه", "Turkish Lira", 2_490, 0, "🇹🇷", 0.006, ["lira", "لیر", "try"]],
  ["chf-irt", "CHF/IRT", "فرانک سوئیس", "Swiss Franc", 118_600, 0, "🇨🇭", 0.004, ["franc", "فرانک", "chf"]],
  ["cad-irt", "CAD/IRT", "دلار کانادا", "Canadian Dollar", 74_900, 0, "🍁", 0.004, ["cad", "دلار کانادا", "کانادا"]],
  ["aud-irt", "AUD/IRT", "دلار استرالیا", "Australian Dollar", 67_300, 0, "🦘", 0.004, ["aud", "دلار استرالیا", "استرالیا"]],
  ["cny-irt", "CNY/IRT", "یوان چین", "Chinese Yuan", 14_410, 0, "🇨🇳", 0.003, ["yuan", "یوان", "cny"]],
  ["jpy-irt", "JPY/IRT", "ین ژاپن", "Japanese Yen", 692, 0, "🇯🇵", 0.004, ["yen", "ین", "jpy"]],
  ["rub-irt", "RUB/IRT", "روبل روسیه", "Russian Ruble", 1_152, 0, "🇷🇺", 0.006, ["ruble", "روبل", "rub"]],
  ["usdt-irt", "USDT/IRT", "تتر", "Tether (market)", 103_900, 0, "🟢", 0.005, ["tether", "تتر", "usdt"]],
]);

const GOLD = make("gold", [
  ["gold-18k", "XAU-18K", "طلای ۱۸ عیار (گرم)", "Gold 18k (gram)", 8_365_000, 0, "🥇", 0.01, ["طلای ۱۸", "گرم طلا", "gold 18", "18k"], { unitFa: "گرم", unitEn: "gram" }],
  ["gold-24k", "XAU-24K", "طلای ۲۴ عیار (گرم)", "Gold 24k (gram)", 11_153_000, 0, "🟡", 0.01, ["طلای ۲۴", "طلای آب‌شده", "gold 24", "24k"], { unitFa: "گرم", unitEn: "gram" }],
  ["gold-mithqal", "XAU-MTH", "مظنه طلای ۱۷ عیار", "Gold mithqal 17k", 36_120_000, 0, "⚖️", 0.01, ["مثقال", "مظنه", "mithqal"], { unitFa: "مثقال", unitEn: "mithqal" }],
  ["xau-usd", "XAU/USD", "انس جهانی طلا", "Gold ounce (intl)", 3_318, 0, "🌐", 0.009, ["ounce", "انس طلا", "gold ounce", "xau"], { quoteCurrency: USD, unitFa: "اونس", unitEn: "ounce" }],
  ["xag-usd", "XAG/USD", "انس جهانی نقره", "Silver ounce (intl)", 38_4, 2, "⚪", 0.012, ["silver", "نقره", "xag"], { quoteCurrency: USD, unitFa: "اونس", unitEn: "ounce" }],
  ["silver-gram", "XAG-999", "نقره ۹۹۹ (گرم)", "Silver 999 (gram)", 127_600, 0, "🔘", 0.012, ["نقره گرمی", "silver gram"], { unitFa: "گرم", unitEn: "gram" }],
]);

const COINS = make("coin", [
  ["coin-emami", "IRR-EMM", "سکه امامی (تمام)", "Emami coin", 78_400_000, 0, "🪙", 0.012, ["امامی", "سکه امامی", "emami"]],
  ["coin-bahar", "IRR-BHR", "سکه بهار آزادی (تمام)", "Bahar Azadi coin", 72_100_000, 0, "🌻", 0.012, ["بهار آزادی", "bahar azadi"]],
  ["coin-half", "IRR-HLF", "نیم‌سکه بهار آزادی", "Half Azadi coin", 41_300_000, 0, "🌗", 0.012, ["نیم سکه", "half coin"]],
  ["coin-quarter", "IRR-QTR", "ربع‌سکه بهار آزادی", "Quarter Azadi coin", 26_600_000, 0, "🌘", 0.012, ["ربع سکه", "quarter coin"]],
  ["coin-gerami", "IRR-1G", "سکه گرمی (۱ گرم)", "1g coin", 13_720_000, 0, "🌑", 0.013, ["گرمی", "سکه گرمی", "1g"]],
]);

const CRYPTO = make("crypto", [
  ["btc", "BTC", "بیت‌کوین", "Bitcoin", 0, 0, "₿", 0.035, ["bitcoin", "بیتکوین", "بیت کوین"], { cgId: "bitcoin" }],
  ["eth", "ETH", "اتریوم", "Ethereum", 0, 0, "Ξ", 0.04, ["ethereum", "اتریوم"], { cgId: "ethereum" }],
  ["usdt", "USDT", "تتر", "Tether", 0, 3, "🟢", 0.001, ["tether", "تتر"], { cgId: "tether" }],
  ["bnb", "BNB", "بایننس کوین", "BNB", 0, 1, "🔶", 0.035, ["binance", "بایننس"], { cgId: "binancecoin" }],
  ["sol", "SOL", "سولانا", "Solana", 0, 1, "◎", 0.05, ["solana", "سولانا"], { cgId: "solana" }],
  ["xrp", "XRP", "ریپل", "XRP", 0, 3, "✕", 0.045, ["ripple", "ریپل"], { cgId: "ripple" }],
  ["usdc", "USDC", "یو‌اس‌دی‌کوین", "USD Coin", 0, 3, "🔵", 0.001, ["usdc"], { cgId: "usd-coin" }],
  ["doge", "DOGE", "دوج‌کوین", "Dogecoin", 0, 4, "🐕", 0.06, ["doge", "دوج"], { cgId: "dogecoin" }],
  ["ton", "TON", "تون‌کوین", "Toncoin", 0, 3, "💎", 0.05, ["toncoin", "تون"], { cgId: "the-open-network" }],
  ["ada", "ADA", "کاردانو", "Cardano", 0, 3, "₳", 0.05, ["cardano", "کاردانو"], { cgId: "cardano" }],
  ["trx", "TRX", "ترون", "TRON", 0, 4, "🔺", 0.04, ["tron", "ترون"], { cgId: "tron" }],
  ["shib", "SHIB", "شیبا اینو", "Shiba Inu", 0, 7, "🐶", 0.07, ["shiba", "شیبا"], { cgId: "shiba-inu" }],
  ["dot", "DOT", "پولکادات", "Polkadot", 0, 2, "⬤", 0.05, ["polkadot", "پولکادات"], { cgId: "polkadot" }],
  ["link", "LINK", "چین‌لینک", "Chainlink", 0, 2, "🔗", 0.05, ["chainlink", "چینلینک"], { cgId: "chainlink" }],
  ["avax", "AVAX", "آوالانچ", "Avalanche", 0, 2, "🔺", 0.055, ["avalanche", "آوالانچ"], { cgId: "avalanche-2" }],
  ["ltc", "LTC", "لایت‌کوین", "Litecoin", 0, 1, "Ł", 0.04, ["litecoin", "لایتکوین"], { cgId: "litecoin" }],
  ["bch", "BCH", "بیت‌کوین کش", "Bitcoin Cash", 0, 1, "🅑", 0.045, ["bitcoin cash", "بیتکوین کش"], { cgId: "bitcoin-cash" }],
  ["xlm", "XLM", "استلار", "Stellar", 0, 4, "✷", 0.05, ["stellar", "استلار"], { cgId: "stellar" }],
  ["etc", "ETC", "اتریوم کلاسیک", "Ethereum Classic", 0, 2, "🗨", 0.05, ["ethereum classic"], { cgId: "ethereum-classic" }],
  ["atom", "ATOM", "کازموس", "Cosmos", 0, 2, "⚛", 0.05, ["cosmos", "کازموس"], { cgId: "cosmos" }],
  ["near", "NEAR", "نیر پروتکل", "NEAR Protocol", 0, 2, "Ⓝ", 0.06, ["near"], { cgId: "near" }],
  ["apt", "APT", "اپتوس", "Aptos", 0, 3, "🅐", 0.06, ["aptos", "اپتوس"], { cgId: "aptos" }],
  ["arb", "ARB", "آربیتروم", "Arbitrum", 0, 3, "🔵", 0.06, ["arbitrum", "آربیتروم"], { cgId: "arbitrum" }],
  ["op", "OP", "اپتیمیزم", "Optimism", 0, 3, "🔴", 0.06, ["optimism", "اپتیمیزم"], { cgId: "optimism" }],
]);

const STOCKS = make("stock", [
  ["tse-tedpix", "TEDPIX", "شاخص کل بورس تهران", "TEDPIX index", 3_050_000, 0, "📈", 0.008, ["شاخص کل", "tedpix", "بورس"], { quoteCurrency: "", unitFa: "امتیاز", unitEn: "points" }],
  ["tse-eqw", "EQW", "شاخص هم‌وزن", "Equal-weight index", 852_000, 0, "📊", 0.008, ["هم‌وزن"], { quoteCurrency: "", unitFa: "امتیاز", unitEn: "points" }],
  ["tse-foolad", "فولاد", "فولاد مبارکه", "Foolad Co.", 4_310, 0, "🏭", 0.02, ["foolad", "فولاد"]],
  ["tse-fmeli", "فملی", "ملی مس ایران", "National Copper", 8_940, 0, "⛏️", 0.022, ["فملی", "مس"]],
  ["tse-shapna", "شپنا", "پالایش نفت اصفهان", "Isfahan Refinery", 6_820, 0, "🛢️", 0.02, ["شپنا", "پالایش"]],
  ["tse-khodro", "خودرو", "ایران خودرو", "Iran Khodro", 1_168, 0, "🚗", 0.025, ["خودرو", "ایران خودرو"]],
  ["tse-webmelat", "وبملت", "بانک ملت", "Bank Mellat", 2_905, 0, "🏦", 0.018, ["وبملت", "ملت"]],
  ["tse-shasta", "شستا", "سرمایه‌گذاری تامین اجتماعی", "Shasta", 1_252, 0, "🏢", 0.02, ["شستا", "تامین"]],
  ["tse-kgol", "کگل", "معدنی و صنعتی گل‌گهر", "Gol-e-Gohar", 4_930, 0, "⛰️", 0.022, ["گل گهر", "کگل"]],
  ["tse-parsan", "پارسان", "پارسان گاز", "Parsan Gas", 908, 0, "🔥", 0.025, ["پارسان"]],
]);

const COMMODITIES = make("commodity", [
  ["brent", "BRENT", "نفت برنت", "Brent crude", 64_2, 2, "🛢️", 0.018, ["brent", "برنت", "نفت"], { quoteCurrency: USD, unitFa: "بشکه", unitEn: "barrel" }],
  ["wti", "WTI", "نفت وست تگزاس", "WTI crude", 60_1, 2, "🛢️", 0.018, ["wti", "نفت آمریکا"], { quoteCurrency: USD, unitFa: "بشکه", unitEn: "barrel" }],
  ["xpt-usd", "XPT/USD", "انس جهانی پلاتین", "Platinum ounce", 1_352, 1, "⬜", 0.016, ["platinum", "پلاتین"], { quoteCurrency: USD, unitFa: "اونس", unitEn: "ounce" }],
  ["xpd-usd", "XPD/USD", "انس جهانی پالادیوم", "Palladium ounce", 1_105, 1, "⬛", 0.018, ["palladium", "پالادیوم"], { quoteCurrency: USD, unitFa: "اونس", unitEn: "ounce" }],
  ["ng", "NATGAS", "گاز طبیعی", "Natural gas", 3_15, 3, "💨", 0.03, ["gas", "گاز"], { quoteCurrency: USD, unitFa: "میلیون BTU", unitEn: "MMBtu" }],
]);

export const ASSETS: Asset[] = [
  ...CURRENCIES,
  ...GOLD,
  ...COINS,
  ...CRYPTO,
  ...STOCKS,
  ...COMMODITIES,
];

const BY_ID = new Map(ASSETS.map((a) => [a.id, a]));
const BY_CG = new Map(ASSETS.filter((a) => a.cgId).map((a) => [a.cgId!, a]));

export function getAsset(id: string): Asset | undefined {
  return BY_ID.get(id);
}

export function getAssetByCgId(cgId: string): Asset | undefined {
  return BY_CG.get(cgId);
}

export function assetsByCategory(category: AssetCategory): Asset[] {
  return ASSETS.filter((a) => a.category === category);
}

/** Default featured assets for the home dashboard on first launch. */
export const DEFAULT_FEATURED = ["usd-irt", "gold-18k", "coin-emami", "btc"];

/** Crypto ids list for the CoinGecko batch fetch. */
export const CG_IDS = ASSETS.filter((a) => a.cgId)
  .map((a) => a.cgId)
  .join(",");

/** Search across names, symbols and aliases (fa + en). */
export function searchAssets(query: string): Asset[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const normalizeFa = (s: string) =>
    s
      .replace(/ي/g, "ی")
      .replace(/ك/g, "ک")
      .replace(/\u200c/g, " ")
      .replace(/\s+/g, " ")
      .toLowerCase();
  const nq = normalizeFa(q);
  return ASSETS.filter((a) => {
    const hay = [
      a.symbol.toLowerCase(),
      a.nameEn.toLowerCase(),
      normalizeFa(a.nameFa),
      ...a.aliases.map(normalizeFa),
      a.id,
    ];
    return hay.some((h) => h.includes(nq) || (nq.length >= 3 && h.startsWith(nq) === false && h.includes(nq)));
  });
}
