/**
 * «چقدر؟» — normalized market data domain model.
 * Adapted from blueprint section 6: one model that can represent all markets.
 * Timestamps are always UTC epoch millis; formatting happens at the UI layer.
 */

export type AssetCategory =
  | "currency"
  | "gold"
  | "coin"
  | "crypto"
  | "stock"
  | "commodity";

/** How fresh / trustworthy a quote is. */
export type Freshness = "live" | "delayed" | "stale" | "demo" | "unavailable";

/** Data source identity carried through the whole pipeline (attribution rule). */
export interface SourceInfo {
  /** stable provider id, e.g. "coingecko" | "demo-engine" */
  providerId: string;
  /** human readable source label per locale */
  labelFa: string;
  labelEn: string;
  /** URL of the source for attribution */
  url?: string;
}

export interface Asset {
  /** canonical stable id — never a display name (blueprint 6) */
  id: string;
  category: AssetCategory;
  /** canonical ticker/symbol, e.g. "USD/IRT" or "BTC" */
  symbol: string;
  nameFa: string;
  nameEn: string;
  /** the currency the quote is denominated in, e.g. "IRT" (toman), "USD" */
  quoteCurrency: string;
  /** unit of measure, e.g. "each", "gram", "mithqal", "ounce" */
  unitFa: string;
  unitEn: string;
  /** suggested display decimals */
  decimals: number;
  /** search aliases (persian + english + ticker) */
  aliases: string[];
  /** emoji/text badge rendered in lists */
  badge: string;
  /** relative daily volatility used by the demo engine (0=flat) */
  vol?: number;
  /** demo-engine baseline price in quote currency (NOT a real quote) */
  basePrice?: number;
  /** CoinGecko asset id for live crypto quotes */
  cgId?: string;
}

export interface Quote {
  assetId: string;
  price: number;
  /** absolute change vs previous close */
  changeAbsolute: number;
  /** percent change vs previous close */
  changePercent: number;
  /** provider's own timestamp if available */
  sourceTime: number;
  /** when we fetched it */
  fetchTime: number;
  /** last time this asset had a successful update (may be older on failure) */
  lastSuccessTime: number;
  source: SourceInfo;
  freshness: Freshness;
  /** optional stats when genuinely available */
  low24h?: number;
  high24h?: number;
}

export interface PricePoint {
  /** UTC epoch millis */
  t: number;
  v: number;
}

export type HistoryRange =
  | "24h"
  | "7d"
  | "30d"
  | "90d"
  | "6m"
  | "1y"
  | "5y"
  | "max";

export interface HistorySeries {
  assetId: string;
  range: HistoryRange;
  points: PricePoint[];
  source: SourceInfo;
  freshness: Freshness;
}

/** GET /api/markets response */
export interface MarketsResponse {
  quotes: Quote[];
  serverTime: number;
  /** global provider health info */
  providers: {
    providerId: string;
    ok: boolean;
    noteFa?: string;
    noteEn?: string;
  }[];
}

/** GET /api/history response */
export interface HistoryResponse {
  assetId: string;
  range: HistoryRange;
  points: PricePoint[];
  source: SourceInfo;
  freshness: Freshness;
}

export const CATEGORY_ORDER: AssetCategory[] = [
  "currency",
  "gold",
  "coin",
  "crypto",
  "stock",
  "commodity",
];

export const RANGE_ORDER: HistoryRange[] = [
  "24h",
  "7d",
  "30d",
  "90d",
  "6m",
  "1y",
  "5y",
  "max",
];
