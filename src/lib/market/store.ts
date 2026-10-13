"use client";

import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import type { Locale } from "@/lib/i18n";
import type { DigitStyle } from "./format";
import type { Quote } from "./types";
import { DEFAULT_FEATURED } from "./catalog";

/* ── domain types for user data ─────────────────────────────── */

export type AlertType = "above" | "below" | "pctUp" | "pctDown";

export interface AlertRule {
  id: string;
  assetId: string;
  type: AlertType;
  value: number;
  enabled: boolean;
  createdAt: number;
  lastTriggeredAt?: number;
  /** re-arm logic: alert fires once per crossing, re-arms when price crosses back */
  armed: boolean;
}

export interface Holding {
  id: string;
  assetId: string;
  quantity: number;
  avgCost: number;
  createdAt: number;
}

export interface DashboardConfig {
  showMiniCharts: boolean;
  showDailyChange: boolean;
  density: "compact" | "comfortable";
  sections: { featured: boolean; watchlist: boolean; movers: boolean };
}

/* ── store ──────────────────────────────────────────────────── */

interface CheghadrState {
  // settings
  locale: Locale;
  digitStyle: DigitStyle;
  calendar: "jalali" | "gregorian";
  refreshIntervalMinutes: 0 | 1 | 2 | 5;
  quietHours: boolean;

  setLocale: (l: Locale) => void;
  setDigitStyle: (d: DigitStyle) => void;
  setCalendar: (c: "jalali" | "gregorian") => void;
  setRefreshInterval: (m: 0 | 1 | 2 | 5) => void;
  setQuietHours: (q: boolean) => void;

  // watchlist / favorites
  favorites: string[];
  toggleFavorite: (assetId: string) => void;
  isFavorite: (assetId: string) => boolean;

  // dashboard customization (blueprint §5 home screen)
  featured: string[];
  dashboard: DashboardConfig;
  setFeatured: (ids: string[]) => void;
  toggleFeatured: (assetId: string) => void;
  setDashboard: (patch: Partial<DashboardConfig>) => void;
  resetDashboard: () => void;

  // alerts
  alerts: AlertRule[];
  addAlert: (a: Omit<AlertRule, "id" | "createdAt" | "armed">) => AlertRule;
  removeAlert: (id: string) => void;
  updateAlert: (id: string, patch: Partial<AlertRule>) => void;

  // converter
  recentPairs: [string, string][];
  pushPair: (from: string, to: string) => void;

  // search history
  searchHistory: string[];
  pushSearch: (q: string) => void;
  clearSearchHistory: () => void;

  // portfolio
  holdings: Holding[];
  addHolding: (assetId: string, quantity: number, avgCost: number) => void;
  removeHolding: (id: string) => void;

  // offline cache of last good quotes (blueprint §12)
  cache: { quotes: Quote[]; ts: number } | null;
  setCache: (quotes: Quote[], ts: number) => void;
  clearCache: () => void;
}

const DEFAULT_DASHBOARD: DashboardConfig = {
  showMiniCharts: true,
  showDailyChange: true,
  density: "comfortable",
  sections: { featured: true, watchlist: true, movers: true },
};

export const useCheghadr = create<CheghadrState>()(
  persist(
    (set, get) => ({
      locale: "fa",
      digitStyle: "persian",
      calendar: "jalali",
      refreshIntervalMinutes: 2,
      quietHours: false,

      setLocale: (locale) => set({ locale }),
      setDigitStyle: (digitStyle) => set({ digitStyle }),
      setCalendar: (calendar) => set({ calendar }),
      setRefreshInterval: (refreshIntervalMinutes) => set({ refreshIntervalMinutes }),
      setQuietHours: (quietHours) => set({ quietHours }),

      favorites: [],
      toggleFavorite: (assetId) => {
        const cur = get().favorites;
        set({
          favorites: cur.includes(assetId)
            ? cur.filter((x) => x !== assetId)
            : [...cur, assetId],
        });
      },
      isFavorite: (assetId) => get().favorites.includes(assetId),

      featured: DEFAULT_FEATURED,
      dashboard: DEFAULT_DASHBOARD,
      setFeatured: (featured) => set({ featured }),
      toggleFeatured: (assetId) => {
        const cur = get().featured;
        set({
          featured: cur.includes(assetId)
            ? cur.filter((x) => x !== assetId)
            : [...cur, assetId].slice(0, 8),
        });
      },
      setDashboard: (patch) =>
        set({ dashboard: { ...get().dashboard, ...patch } }),
      resetDashboard: () => set({ dashboard: DEFAULT_DASHBOARD, featured: DEFAULT_FEATURED }),

      alerts: [],
      addAlert: (a) => {
        const rule: AlertRule = {
          ...a,
          id: crypto.randomUUID(),
          createdAt: Date.now(),
          armed: true,
        };
        set({ alerts: [...get().alerts, rule] });
        return rule;
      },
      removeAlert: (id) => set({ alerts: get().alerts.filter((x) => x.id !== id) }),
      updateAlert: (id, patch) =>
        set({
          alerts: get().alerts.map((x) => (x.id === id ? { ...x, ...patch } : x)),
        }),

      recentPairs: [],
      pushPair: (from, to) => {
        const cur = get().recentPairs.filter(([f, t]) => !(f === from && t === to));
        set({ recentPairs: [[from, to], ...cur].slice(0, 5) });
      },

      searchHistory: [],
      pushSearch: (q) => {
        const t = q.trim();
        if (!t) return;
        const cur = get().searchHistory.filter((x) => x !== t);
        set({ searchHistory: [t, ...cur].slice(0, 6) });
      },
      clearSearchHistory: () => set({ searchHistory: [] }),

      holdings: [],
      addHolding: (assetId, quantity, avgCost) =>
        set({
          holdings: [
            ...get().holdings,
            {
              id: crypto.randomUUID(),
              assetId,
              quantity,
              avgCost,
              createdAt: Date.now(),
            },
          ],
        }),
      removeHolding: (id) =>
        set({ holdings: get().holdings.filter((h) => h.id !== id) }),

      cache: null,
      setCache: (quotes, ts) => set({ cache: { quotes, ts } }),
      clearCache: () => set({ cache: null }),
    }),
    {
      name: "cheghadr-store",
      version: 1,
      storage: createJSONStorage(() => localStorage),
    },
  ),
);
