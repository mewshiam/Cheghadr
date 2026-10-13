"use client";

import React, { useCallback, useEffect, useState } from "react";
import {
  Bell,
  BellRing,
  Share2,
  Star,
  StarOff,
} from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useToast } from "@/hooks/use-toast";
import { useI18n } from "@/lib/i18n";
import { useCheghadr, type AlertType } from "@/lib/market/store";
import { getAsset } from "@/lib/market/catalog";
import { fetchHistory } from "@/lib/market/client";
import { formatNumber, formatDateTime, quoteCurrencyLabel } from "@/lib/market/format";
import type { Asset, HistoryRange, HistoryResponse, Quote } from "@/lib/market/types";
import { RANGE_ORDER } from "@/lib/market/types";
import { LineChart } from "./charts";
import { AssetAvatar, ChangeBadge, FreshnessBadge, PriceText } from "./common";

const RANGE_KEY: Record<HistoryRange, "range24h" | "range7d" | "range30d" | "range90d" | "range6m" | "range1y" | "range5y" | "rangeMax"> = {
  "24h": "range24h",
  "7d": "range7d",
  "30d": "range30d",
  "90d": "range90d",
  "6m": "range6m",
  "1y": "range1y",
  "5y": "range5y",
  max: "rangeMax",
};

export function AssetDetail({
  assetId,
  open,
  onOpenChange,
}: {
  assetId: string | null;
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  const asset: Asset | null = assetId ? getAsset(assetId) ?? null : null;
  const { t, locale } = useI18n();
  const { toast } = useToast();
  const digitStyle = useCheghadr((s) => s.digitStyle);
  const calendar = useCheghadr((s) => s.calendar);
  const favorites = useCheghadr((s) => s.favorites);
  const toggleFavorite = useCheghadr((s) => s.toggleFavorite);
  const addAlert = useCheghadr((s) => s.addAlert);
  const alerts = useCheghadr((s) => s.alerts);

  const [range, setRange] = useState<HistoryRange>("30d");
  const [history, setHistory] = useState<HistoryResponse | null>(null);
  const [histLoading, setHistLoading] = useState(false);
  const [alertType, setAlertType] = useState<AlertType>("above");
  const [alertValue, setAlertValue] = useState("");
  const [alertOpen, setAlertOpen] = useState(false);

  const quote: Quote | undefined = useCheghadr.getState().cache?.quotes.find(
    (q) => q.assetId === assetId,
  );

  const loadHistory = useCallback(
    async (r: HistoryRange) => {
      if (!assetId) return;
      setHistLoading(true);
      const h = await fetchHistory(assetId, r);
      setHistory(h);
      setHistLoading(false);
    },
    [assetId],
  );

  useEffect(() => {
    if (open && assetId) void loadHistory(range);
  }, [open, assetId, range, loadHistory]);

  if (!asset) return null;

  const isFav = favorites.includes(asset.id);
  const pctUp = (history?.points.at(-1)?.v ?? 0) >= (history?.points[0]?.v ?? 0);
  const hasAlert = alerts.some((a) => a.assetId === asset.id);

  const share = async () => {
    if (!quote) return;
    const price = formatNumber(quote.price, { decimals: asset.decimals, locale, digits: digitStyle });
    const text = `${locale === "fa" ? asset.nameFa : asset.nameEn}: ${price} ${quoteCurrencyLabel(asset.quoteCurrency, locale)}`;
    const nav = navigator as Navigator & { share?: (d: { title: string; text: string }) => Promise<void> };
    if (nav.share) {
      try {
        await nav.share({ title: t("appName"), text });
        return;
      } catch {
        /* fall through to clipboard */
      }
    }
    await navigator.clipboard.writeText(text);
    toast({ description: t("shared") });
  };

  const createAlert = () => {
    const v = parseFloat(alertValue);
    if (!Number.isFinite(v) || v <= 0) return;
    const dup = alerts.some(
      (a) => a.assetId === asset.id && a.type === alertType && a.value === v,
    );
    if (dup) {
      toast({ description: t("alertExists") });
      return;
    }
    addAlert({ assetId: asset.id, type: alertType, value: v, enabled: true });
    setAlertOpen(false);
    setAlertValue("");
    toast({ description: t("alertCreated") });
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        className="h-[92dvh] overflow-y-auto rounded-t-2xl p-0 sm:max-w-lg sm:mx-auto"
      >
        <div className="mx-auto w-full max-w-lg px-4 pb-8">
          <SheetHeader className="p-0 pt-5 text-start">
            <div className="flex items-center gap-3">
              <AssetAvatar asset={asset} size={44} />
              <div className="min-w-0 flex-1">
                <SheetTitle className="text-base leading-6">
                  {locale === "fa" ? asset.nameFa : asset.nameEn}
                </SheetTitle>
                <SheetDescription className="flex items-center gap-2 text-xs">
                  <span dir="ltr">{asset.symbol}</span>
                  <FreshnessBadge freshness={quote?.freshness ?? "unavailable"} />
                  <span className="text-muted-foreground">
                    {locale === "fa" ? quote?.source.labelFa : quote?.source.labelEn}
                  </span>
                </SheetDescription>
              </div>
              <Button
                variant="ghost"
                size="icon"
                onClick={() => toggleFavorite(asset.id)}
                aria-label={isFav ? t("removeFromWatchlist") : t("addToWatchlist")}
              >
                {isFav ? (
                  <Star className="h-5 w-5 fill-gold text-gold" />
                ) : (
                  <Star className="h-5 w-5" />
                )}
              </Button>
            </div>
          </SheetHeader>

          {/* quote */}
          <div className="mt-4 rounded-2xl bg-card p-4">
            <div className="flex items-end justify-between gap-2">
              <div>
                <PriceText
                  value={quote?.price ?? NaN}
                  asset={asset}
                  digits={digitStyle}
                  className="text-3xl"
                />
                <p className="mt-1 text-[11px] text-muted-foreground">
                  {quote && t("lastUpdate") + ": " + formatDateTime(Math.min(quote.sourceTime, quote.fetchTime), locale, calendar, digitStyle)}
                </p>
              </div>
              {quote && <ChangeBadge quote={quote} digits={digitStyle} />}
            </div>
            {quote?.high24h !== undefined && quote?.low24h !== undefined && (
              <div className="mt-3 flex justify-between text-xs text-muted-foreground tnum" dir="ltr">
                <span>{t("low24h")}: {formatNumber(quote.low24h, { decimals: asset.decimals, locale, digits: digitStyle })}</span>
                <span>{t("high24h")}: {formatNumber(quote.high24h, { decimals: asset.decimals, locale, digits: digitStyle })}</span>
              </div>
            )}
            <div className="mt-3 flex gap-2">
              <Button variant="secondary" size="sm" className="flex-1" onClick={() => setAlertOpen(true)}>
                {hasAlert ? <BellRing className="me-1 h-4 w-4" /> : <Bell className="me-1 h-4 w-4" />}
                {t("createAlert")}
              </Button>
              <Button variant="secondary" size="sm" className="flex-1" onClick={share}>
                <Share2 className="me-1 h-4 w-4" />
                {t("shareTitle")}
              </Button>
            </div>
          </div>

          {/* chart */}
          <div className="mt-4 rounded-2xl bg-card p-3">
            <Tabs value={range} onValueChange={(v) => setRange(v as HistoryRange)}>
              <TabsList className="flex h-9 w-full justify-start overflow-x-auto bg-muted/60">
                {RANGE_ORDER.map((r) => (
                  <TabsTrigger key={r} value={r} className="px-2.5 text-xs">
                    {t(RANGE_KEY[r])}
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
            <div className="mt-3">
              {histLoading ? (
                <div className="grid h-40 place-items-center text-sm text-muted-foreground">
                  {t("loading")}
                </div>
              ) : history && history.points.length > 1 ? (
                <LineChart
                  points={history.points}
                  up={pctUp}
                  decimals={asset.decimals}
                  locale={locale}
                  calendar={calendar}
                  digits={digitStyle}
                />
              ) : (
                <div className="grid h-32 place-items-center text-sm text-muted-foreground">
                  {t("chartUnavailable")}
                </div>
              )}
            </div>
          </div>

          {/* alert creation */}
          {alertOpen && (
            <div className="mt-4 rounded-2xl border bg-card p-4">
              <p className="mb-2 text-sm font-bold">{t("createAlert")}</p>
              <Tabs value={alertType} onValueChange={(v) => setAlertType(v as AlertType)}>
                <TabsList className="grid w-full grid-cols-4">
                  <TabsTrigger value="above">{t("alertAbove")}</TabsTrigger>
                  <TabsTrigger value="below">{t("alertBelow")}</TabsTrigger>
                  <TabsTrigger value="pctUp">↑ %</TabsTrigger>
                  <TabsTrigger value="pctDown">↓ %</TabsTrigger>
                </TabsList>
              </Tabs>
              <div className="mt-3 flex gap-2">
                <Input
                  type="number"
                  inputMode="decimal"
                  dir="ltr"
                  placeholder={
                    alertType.startsWith("pct")
                      ? t("alertPct")
                      : t("alertTarget")
                  }
                  value={alertValue}
                  onChange={(e) => setAlertValue(e.target.value)}
                />
                <Button onClick={createAlert}>{t("createAlert")}</Button>
              </div>
              <p className="mt-2 text-[11px] leading-4 text-muted-foreground">
                {t("alertLocalNote")}
              </p>
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

export { StarOff };
