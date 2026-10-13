"use client";

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { Quote } from "@/lib/market/types";
import { fetchMarkets } from "@/lib/market/client";
import { useCheghadr } from "@/lib/market/store";
import { evaluateAlerts } from "@/lib/market/alerts";
import { getAsset } from "@/lib/market/catalog";
import { toast } from "@/hooks/use-toast";
import { useI18n } from "@/lib/i18n";
import { formatNumber, quoteCurrencyLabel } from "@/lib/market/format";

interface MarketsCtx {
  quotes: Quote[];
  byId: Map<string, Quote>;
  loading: boolean;
  offline: boolean;
  lastUpdate: number | null;
  refresh: () => Promise<void>;
  refreshing: boolean;
}

const Ctx = createContext<MarketsCtx>({
  quotes: [],
  byId: new Map(),
  loading: true,
  offline: false,
  lastUpdate: null,
  refresh: async () => {},
  refreshing: false,
});

export function MarketsProvider({ children }: { children: React.ReactNode }) {
  const [quotes, setQuotes] = useState<Quote[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [offline, setOffline] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<number | null>(null);
  const { locale, t } = useI18n();
  const intervalMin = useCheghadr((s) => s.refreshIntervalMinutes);
  const storeRef = useRef(useCheghadr.getState());
  storeRef.current = useCheghadr.getState();

  const doRefresh = useCallback(
    async (initial = false) => {
      if (initial) setLoading(true);
      else setRefreshing(true);
      try {
        const res = await fetchMarkets();
        setQuotes(res.quotes);
        setOffline(res.offline);
        setLastUpdate(res.serverTime);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [],
  );

  // initial load
  useEffect(() => {
    void doRefresh(true);
  }, [doRefresh]);

  // alert evaluation after every snapshot change
  useEffect(() => {
    if (!quotes.length) return;
    const { alerts, updateAlert, quietHours } = storeRef.current;
    if (!alerts.length) return;
    const { fired, updates } = evaluateAlerts(alerts, quotes);
    if (updates.length) {
      for (const u of updates) updateAlert(u.id, { armed: u.armed, lastTriggeredAt: u.lastTriggeredAt });
    }
    for (const f of fired) {
      const asset = getAsset(f.rule.assetId);
      if (!asset) continue;
      const priceStr = formatNumber(f.quote.price, {
        decimals: asset.decimals,
        locale,
        digits: storeRef.current.digitStyle,
      }) + " " + quoteCurrencyLabel(asset.quoteCurrency, locale);
      const cond = t(
        f.rule.type === "above" ? "alertAbove" : f.rule.type === "below" ? "alertBelow" : f.rule.type === "pctUp" ? "alertPctUp" : "alertPctDown",
        { n: formatNumber(f.rule.value, { decimals: f.rule.type.startsWith("pct") ? 1 : asset.decimals, locale, digits: storeRef.current.digitStyle }) },
      );
      const body = t("alertFiredBody", { name: locale === "fa" ? asset.nameFa : asset.nameEn, price: priceStr, cond });
      if (!quietHours) {
        toast({ title: t("alertFiredTitle"), description: body });
      }
      if (typeof window !== "undefined" && "Notification" in window && Notification.permission === "granted") {
        new Notification(t("alertFiredTitle"), { body, icon: "/logo.svg" });
      }
    }
  }, [quotes, locale, t]);

  // periodic refresh
  useEffect(() => {
    if (!intervalMin) return;
    const ms = intervalMin * 60_000;
    const id = setInterval(() => {
      if (document.visibilityState === "visible") void doRefresh();
    }, ms);
    const onVis = () => {
      if (document.visibilityState === "visible") void doRefresh();
    };
    document.addEventListener("visibilitychange", onVis);
    return () => {
      clearInterval(id);
      document.removeEventListener("visibilitychange", onVis);
    };
  }, [intervalMin, doRefresh]);

  const byId = useMemo(() => new Map(quotes.map((q) => [q.assetId, q])), [quotes]);

  const value = useMemo(
    () => ({ quotes, byId, loading, offline, lastUpdate, refresh: () => doRefresh(), refreshing }),
    [quotes, byId, loading, offline, lastUpdate, doRefresh, refreshing],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useMarkets() {
  return useContext(Ctx);
}
