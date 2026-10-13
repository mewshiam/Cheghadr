"use client";

import type { HistoryRange, HistoryResponse, MarketsResponse, Quote } from "./types";
import { useCheghadr } from "./store";

/**
 * Client data layer: fetch normalized quotes/history, cache last good
 * snapshot for offline display (blueprint §12), expose staleness info.
 */

export const STALE_AFTER_MS = 3 * 60_000;

export interface MarketsResult {
  quotes: Quote[];
  serverTime: number;
  offline: boolean;
  error?: string;
}

export async function fetchMarkets(): Promise<MarketsResult> {
  const store = useCheghadr.getState();
  try {
    const res = await fetch("/api/markets", { cache: "no-store" });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = (await res.json()) as MarketsResponse;
    store.setCache(data.quotes, data.serverTime);
    return { quotes: data.quotes, serverTime: data.serverTime, offline: false };
  } catch (e) {
    const cached = useCheghadr.getState().cache;
    if (cached) {
      return {
        quotes: cached.quotes,
        serverTime: cached.ts,
        offline: true,
        error: e instanceof Error ? e.message : "network error",
      };
    }
    return {
      quotes: [],
      serverTime: Date.now(),
      offline: typeof navigator !== "undefined" && !navigator.onLine,
      error: e instanceof Error ? e.message : "network error",
    };
  }
}

export async function fetchHistory(
  assetId: string,
  range: HistoryRange,
): Promise<HistoryResponse | null> {
  try {
    const res = await fetch(
      `/api/history?asset=${encodeURIComponent(assetId)}&range=${range}`,
      { cache: "no-store" },
    );
    if (!res.ok) return null;
    return (await res.json()) as HistoryResponse;
  } catch {
    return null;
  }
}

export function isStale(ts: number, now = Date.now()): boolean {
  return now - ts > STALE_AFTER_MS;
}
