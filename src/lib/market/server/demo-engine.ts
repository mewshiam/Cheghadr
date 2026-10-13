import type { Quote } from "../types";
import { ASSETS, getAsset } from "../catalog";

/**
 * «چقدر؟» DEMO market engine — server-side only.
 *
 * HONESTY (blueprint §2): these are clearly-labeled SIMULATED prices for the
 * preview build, never presented as real quotes. Deterministic seeded walks
 * keep /api/markets and /api/history mutually consistent within a day.
 */

function hashStr(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function mulberry32(seed: number) {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const DAY_MS = 86_400_000;

function dayIndex(ts: number): number {
  return Math.floor(ts / DAY_MS);
}

/** Deterministic daily return for asset on day n (small random walk step). */
function dailyReturn(assetId: string, day: number, vol: number): number {
  const r = mulberry32(hashStr(`${assetId}#${day}`));
  // fat-tailed-ish step, bounded
  const u = r() - 0.5;
  const v = r() - 0.5;
  return Math.max(-3 * vol, Math.min(3 * vol, (u + v) * vol * 0.7));
}

/** Base price of an asset on a given UTC day (random walk from catalog anchor). */
export function dayBase(assetId: string, day: number): number {
  const asset = getAsset(assetId);
  if (!asset || asset.basePrice === undefined) return NaN;
  const vol = asset.vol ?? 0.01;
  const today = dayIndex(Date.now());
  let price = asset.basePrice;
  // walk forward from anchor day (anchor = today - 400 days) to requested day
  const anchor = today - 400;
  for (let d = anchor + 1; d <= day; d++) {
    price *= 1 + dailyReturn(assetId, d, vol);
  }
  return price;
}

/** Intraday deterministic noise factor at minute resolution (mean-reverting). */
function intradayFactor(assetId: string, ts: number, vol: number): number {
  const minute = Math.floor(ts / 60_000);
  const r = mulberry32(hashStr(`${assetId}~${minute}`));
  const u = r() - 0.5;
  return 1 + u * vol * 0.8;
}

/** Live demo quote for one asset. */
export function demoQuote(assetId: string, now = Date.now()): Quote | null {
  const asset = getAsset(assetId);
  if (!asset || asset.basePrice === undefined) return null;
  const vol = asset.vol ?? 0.01;
  const today = dayIndex(now);
  const base = dayBase(assetId, today);
  const prevBase = dayBase(assetId, today - 1);
  const price = base * intradayFactor(assetId, now, vol);
  const changeAbsolute = price - prevBase;
  const changePercent = (changeAbsolute / prevBase) * 100;
  // deterministic 24h band
  const r = mulberry32(hashStr(`${assetId}!${today}`));
  const band = price * vol * (0.6 + r() * 0.9);
  return {
    assetId,
    price,
    changeAbsolute,
    changePercent,
    sourceTime: now - Math.floor(r() * 40_000),
    fetchTime: now,
    lastSuccessTime: now,
    source: {
      providerId: "demo-engine",
      labelFa: "موتور نمایشی چقدر؟",
      labelEn: "Cheghadr? demo engine",
    },
    freshness: "demo",
    low24h: Math.min(price, prevBase) - band * 0.4,
    high24h: Math.max(price, prevBase) + band * 0.6,
  };
}

/** All demo quotes (Iranian markets + commodities). */
export function demoQuotes(now = Date.now()): Quote[] {
  return ASSETS.filter((a) => a.basePrice !== undefined && a.basePrice > 0)
    .map((a) => demoQuote(a.id, now))
    .filter((q): q is Quote => q !== null);
}

/** Deterministic demo history for a range. */
export function demoHistory(
  assetId: string,
  points: number,
  stepMs: number,
  now = Date.now(),
): { t: number; v: number }[] {
  const asset = getAsset(assetId);
  if (!asset || asset.basePrice === undefined) return [];
  const vol = asset.vol ?? 0.01;
  const out: { t: number; v: number }[] = [];
  const today = dayIndex(now);

  if (stepMs < DAY_MS) {
    // intraday: minute-level walk around today's base, ending at current price
    const steps = Math.min(points, 400);
    const startTs = now - steps * stepMs;
    const r = mulberry32(hashStr(`${assetId}@${today}`));
    const base = dayBase(assetId, today);
    let v = base;
    // pre-generate then rescale so last point ≈ current price
    const raw: number[] = [0];
    for (let i = 1; i <= steps; i++) {
      raw.push((r() - 0.5) * vol * 0.5);
    }
    const current = base * intradayFactor(assetId, now, vol);
    const drift = current / (base * Math.exp(raw.reduce((s, x) => s + x, 0)));
    v = base;
    for (let i = 0; i <= steps; i++) {
      v = base * Math.exp(raw[i]) * (1 + (drift - 1) * (i / steps));
      out.push({ t: startTs + i * stepMs, v });
    }
  } else {
    // multi-day: walk BACK from today's base so series ends at base
    const startDay = today - (points - 1);
    const closes: number[] = new Array(points);
    closes[points - 1] = dayBase(assetId, today);
    for (let i = points - 2; i >= 0; i--) {
      const day = startDay + i + 1;
      closes[i] = closes[i + 1] / (1 + dailyReturn(assetId, day, vol));
    }
    for (let i = 0; i < points; i++) {
      out.push({ t: (startDay + i) * DAY_MS + 12 * 3_600_000, v: closes[i] });
    }
  }
  return out;
}
