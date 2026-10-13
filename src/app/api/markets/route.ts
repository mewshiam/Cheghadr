import { NextResponse } from "next/server";
import type { MarketsResponse, Quote } from "@/lib/market/types";
import { ASSETS } from "@/lib/market/catalog";
import { demoQuotes } from "@/lib/market/server/demo-engine";
import { cgQuoteFor, fetchCgQuotes } from "@/lib/market/server/coingecko";

export const dynamic = "force-dynamic";

/** GET /api/markets — normalized quotes for every catalog asset. */
export async function GET() {
  const now = Date.now();

  const demo = demoQuotes(now);
  const demoById = new Map(demo.map((q) => [q.assetId, q]));

  const cgResult = await fetchCgQuotes(now);
  const quotes: Quote[] = [];

  for (const asset of ASSETS) {
    if (asset.cgId) {
      const q = cgQuoteFor(asset.id, now);
      if (q) quotes.push(q);
    } else {
      const q = demoById.get(asset.id);
      if (q) quotes.push(q);
    }
  }

  const body: MarketsResponse = {
    quotes,
    serverTime: now,
    providers: [
      {
        providerId: "coingecko",
        ok: cgResult.ok,
        noteFa: cgResult.ok ? undefined : (cgResult.note ?? "خطا در دریافت رمزارزها"),
        noteEn: cgResult.note,
      },
      {
        providerId: "demo-engine",
        ok: true,
        noteFa: "قیمت‌های نمایشی (شبیه‌سازی‌شده)",
        noteEn: "Demo prices (simulated)",
      },
    ],
  };

  return NextResponse.json(body, {
    headers: { "cache-control": "no-store" },
  });
}
