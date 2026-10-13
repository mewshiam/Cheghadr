import { NextRequest, NextResponse } from "next/server";
import type { HistoryRange, PricePoint } from "@/lib/market/types";
import { getAsset } from "@/lib/market/catalog";
import { demoHistory } from "@/lib/market/server/demo-engine";
import { fetchCgHistory } from "@/lib/market/server/coingecko";

export const dynamic = "force-dynamic";

const DAY_MS = 86_400_000;

const RANGE_POINTS: Record<HistoryRange, { points: number; stepMs: number }> = {
  "24h": { points: 144, stepMs: 10 * 60_000 },
  "7d": { points: 168, stepMs: 3_600_000 },
  "30d": { points: 120, stepMs: 6 * 3_600_000 },
  "90d": { points: 180, stepMs: 12 * 3_600_000 },
  "6m": { points: 182, stepMs: DAY_MS },
  "1y": { points: 365, stepMs: DAY_MS },
  "5y": { points: 260, stepMs: 7 * DAY_MS },
  max: { points: 400, stepMs: 7 * DAY_MS },
};

const CG_DAYS: Record<HistoryRange, number> = {
  "24h": 1,
  "7d": 7,
  "30d": 30,
  "90d": 90,
  "6m": 180,
  "1y": 365,
  "5y": 1825,
  max: 1825,
};

/** GET /api/history?asset=<id>&range=<range> */
export async function GET(req: NextRequest) {
  const assetId = req.nextUrl.searchParams.get("asset") ?? "";
  const range = (req.nextUrl.searchParams.get("range") ?? "30d") as HistoryRange;
  const asset = getAsset(assetId);
  if (!asset) {
    return NextResponse.json({ error: "unknown asset" }, { status: 400 });
  }
  const cfg = RANGE_POINTS[range] ?? RANGE_POINTS["30d"];
  const now = Date.now();

  if (asset.cgId) {
    const raw = await fetchCgHistory(asset.cgId, CG_DAYS[range], now);
    if (raw && raw.length) {
      // CoinGecko daily interval returns ~5-minute granular points for short
      // ranges; downsample to a reasonable count for the chart.
      const step = Math.max(1, Math.floor(raw.length / cfg.points));
      const points: PricePoint[] = [];
      for (let i = 0; i < raw.length; i += step) {
        points.push({ t: raw[i][0], v: raw[i][1] });
      }
      const last = raw[raw.length - 1];
      if (points[points.length - 1].t !== last[0]) {
        points.push({ t: last[0], v: last[1] });
      }
      return NextResponse.json({
        assetId,
        range,
        points,
        source: {
          providerId: "coingecko",
          labelFa: "کوین‌گکو (زنده)",
          labelEn: "CoinGecko (live)",
          url: "https://www.coingecko.com/",
        },
        freshness: "live",
      });
    }
    return NextResponse.json(
      {
        assetId,
        range,
        points: [],
        source: {
          providerId: "coingecko",
          labelFa: "کوین‌گکو (زنده)",
          labelEn: "CoinGecko (live)",
        },
        freshness: "unavailable",
      },
      { status: 200 },
    );
  }

  const points = demoHistory(assetId, cfg.points, cfg.stepMs, now);
  return NextResponse.json({
    assetId,
    range,
    points,
    source: {
      providerId: "demo-engine",
      labelFa: "موتور نمایشی چقدر؟",
      labelEn: "Cheghadr? demo engine",
    },
    freshness: "demo",
  });
}
