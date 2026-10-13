import type { Quote } from "../types";
import { ASSETS, getAssetByCgId } from "../catalog";

/**
 * CoinGecko provider — LIVE crypto quotes (documented public API, no key).
 * https://api.coingecko.com/api/v3/simple/price & /coins/{id}/market_chart
 * Free tier: ~5-15 req/min → aggressive in-memory caching below.
 */

const QUOTE_TTL_MS = 45_000;
const HISTORY_TTL_MS = 10 * 60_000;

interface CgQuote {
  usd?: number;
  usd_24h_change?: number;
  usd_24h_vol?: number;
  usd_high_24h?: number;
  usd_low_24h?: number;
  last_updated_at?: number;
}

let quoteCache: { data: Map<string, CgQuote>; ts: number } | null = null;
const historyCache = new Map<string, { data: [number, number][]; ts: number }>();

export interface CgFetchResult {
  ok: boolean;
  status?: number;
  note?: string;
}

async function cgFetch(url: string): Promise<Response> {
  const res = await fetch(url, {
    headers: { accept: "application/json" },
    signal: AbortSignal.timeout(8_000),
    cache: "no-store",
  });
  return res;
}

export async function fetchCgQuotes(now = Date.now()): Promise<CgFetchResult> {
  if (quoteCache && now - quoteCache.ts < QUOTE_TTL_MS) {
    return { ok: true };
  }
  const ids = ASSETS.filter((a) => a.cgId)
    .map((a) => a.cgId)
    .join(",");
  try {
    const url =
      `https://api.coingecko.com/api/v3/simple/price?ids=${ids}` +
      `&vs_currencies=usd&include_24hr_change=true&include_24hr_vol=true` +
      `&include_24hr_high=true&include_24hr_low=true&include_last_updated_at=true`;
    const res = await cgFetch(url);
    if (res.status === 429) {
      // rate limited — serve last good cache if any
      if (quoteCache) return { ok: true, note: "rate-limited, serving cache" };
      return { ok: false, status: 429, note: "CoinGecko rate limit (429)" };
    }
    if (!res.ok) {
      if (quoteCache) return { ok: true, note: `cg ${res.status}, serving cache` };
      return { ok: false, status: res.status, note: `CoinGecko HTTP ${res.status}` };
    }
    const json = (await res.json()) as Record<string, CgQuote>;
    quoteCache = { data: new Map(Object.entries(json)), ts: now };
    return { ok: true };
  } catch (e) {
    if (quoteCache) return { ok: true, note: "network error, serving cache" };
    return {
      ok: false,
      status: 503,
      note: `CoinGecko unreachable: ${e instanceof Error ? e.message : "error"}`,
    };
  }
}

export function cgQuoteFor(assetId: string, now = Date.now()): Quote | null {
  const asset = getAssetByCgId(assetId) ?? { cgId: assetId } as never;
  const cgId = (asset as { cgId?: string }).cgId;
  if (!cgId || !quoteCache) return null;
  const q = quoteCache.data.get(cgId);
  if (!q || typeof q.usd !== "number") return null;
  const full = getAssetByCgId(assetId) ?? getAssetByCgId(cgId);
  const decimalsAsset = full?.decimals ?? 2;
  void decimalsAsset;
  const price = q.usd;
  const changePercent = q.usd_24h_change ?? 0;
  const changeAbsolute = price - price / (1 + changePercent / 100);
  return {
    assetId,
    price,
    changeAbsolute,
    changePercent,
    sourceTime: (q.last_updated_at ?? Math.floor(now / 1000)) * 1000,
    fetchTime: now,
    lastSuccessTime: quoteCache.ts,
    source: {
      providerId: "coingecko",
      labelFa: "کوین‌گکو (زنده)",
      labelEn: "CoinGecko (live)",
      url: "https://www.coingecko.com/",
    },
    freshness: "live",
    high24h: q.usd_high_24h,
    low24h: q.usd_low_24h,
  };
}

/** Crypto history: days of daily closes (UTC). */
export async function fetchCgHistory(
  cgId: string,
  days: number,
  now = Date.now(),
): Promise<[number, number][] | null> {
  const key = `${cgId}:${days}`;
  const hit = historyCache.get(key);
  if (hit && now - hit.ts < HISTORY_TTL_MS) return hit.data;
  try {
    const url = `https://api.coingecko.com/api/v3/coins/${cgId}/market_chart?vs_currency=usd&days=${days}&interval=daily`;
    const res = await cgFetch(url);
    if (!res.ok) {
      if (hit) return hit.data;
      return null;
    }
    const json = (await res.json()) as { prices?: [number, number][] };
    if (!json.prices?.length) return hit?.data ?? null;
    historyCache.set(key, { data: json.prices, ts: now });
    return json.prices;
  } catch {
    return hit?.data ?? null;
  }
}
