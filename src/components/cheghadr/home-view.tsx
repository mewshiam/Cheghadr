"use client";

import React, { useMemo, useState } from "react";
import {
  ArrowLeftRight,
  LineChart as LineChartIcon,
  Plus,
  RotateCcw,
  Settings2,
  Star,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Switch } from "@/components/ui/switch";
import { useI18n } from "@/lib/i18n";
import { useCheghadr } from "@/lib/market/store";
import { ASSETS, getAsset, searchAssets } from "@/lib/market/catalog";
import { useMarkets } from "./use-markets";
import { AssetRow } from "./rows";
import { SectionHeader, SkeletonRow, DemoBanner, OfflineBanner } from "./common";
import { formatNumber, formatRelative } from "@/lib/market/format";
import { cn } from "@/lib/utils";

export function HomeView({
  onOpenAsset,
  onGoTo,
}: {
  onOpenAsset: (id: string) => void;
  onGoTo: (tab: "markets" | "converter" | "watchlist") => void;
}) {
  const { t, locale } = useI18n();
  const { quotes, byId, loading, offline, lastUpdate, refreshing, refresh } = useMarkets();
  const digitStyle = useCheghadr((s) => s.digitStyle);
  const calendar = useCheghadr((s) => s.calendar);
  const featured = useCheghadr((s) => s.featured);
  const dashboard = useCheghadr((s) => s.dashboard);
  const setDashboard = useCheghadr((s) => s.setDashboard);
  const resetDashboard = useCheghadr((s) => s.resetDashboard);
  const favorites = useCheghadr((s) => s.favorites);
  const [editOpen, setEditOpen] = useState(false);
  const [pickQuery, setPickQuery] = useState("");

  const featuredQuotes = featured
    .map((id) => ({ asset: getAsset(id), quote: byId.get(id) }))
    .filter((x) => x.asset);

  const movers = useMemo(() => {
    const withQ = quotes
      .map((q) => ({ q, a: getAsset(q.assetId) }))
      .filter((x) => x.a);
    return [...withQ]
      .sort((a, b) => Math.abs(b.q.changePercent) - Math.abs(a.q.changePercent))
      .slice(0, 4);
  }, [quotes]);

  const watchQuotes = favorites
    .map((id) => ({ asset: getAsset(id), quote: byId.get(id) }))
    .filter((x) => x.asset)
    .slice(0, 4);

  const pickResults = pickQuery ? searchAssets(pickQuery).slice(0, 8) : ASSETS.slice(0, 8);

  return (
    <div className="space-y-4 pb-2">
      {offline && <OfflineBanner />}
      <DemoBanner />

      {/* header */}
      <div className="px-1">
        <p className="text-lg font-extrabold leading-7">{t("homeGreeting")}</p>
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            {t("lastUpdate")}:{" "}
            {lastUpdate ? formatRelative(lastUpdate, Date.now(), locale, digitStyle) : "—"}
            {refreshing && ` • ${t("refreshing")}`}
          </span>
          <button
            className="font-semibold text-primary"
            onClick={() => setEditOpen(true)}
          >
            <Settings2 className="me-1 inline h-3.5 w-3.5" />
            {t("editDashboard")}
          </button>
        </div>
      </div>

      {/* featured */}
      {dashboard.sections.featured && (
        <section>
          <SectionHeader
            title={t("featuredPrices")}
            action={
              <button
                className="text-xs font-semibold text-primary"
                onClick={() => setEditOpen(true)}
              >
                <Plus className="me-0.5 inline h-3.5 w-3.5" />
                {t("addFeatured")}
              </button>
            }
          />
          <div className="grid grid-cols-2 gap-2.5">
            {loading
              ? Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="rounded-2xl bg-card p-4">
                    <div className="h-3 w-20 rounded bg-muted skeleton-shimmer" />
                    <div className="mt-3 h-6 w-28 rounded bg-muted skeleton-shimmer" />
                  </div>
                ))
              : featuredQuotes.map(({ asset, quote }) =>
                  asset ? (
                    <button
                      key={asset.id}
                      className="rounded-2xl bg-card p-4 text-start transition-colors hover:bg-accent"
                      onClick={() => onOpenAsset(asset.id)}
                    >
                      <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                        <span aria-hidden>{asset.badge}</span>
                        {locale === "fa" ? asset.nameFa : asset.nameEn}
                      </span>
                      <span className="mt-1.5 block truncate text-lg font-extrabold tnum" dir="ltr">
                        {quote
                          ? formatNumber(quote.price, { decimals: asset.decimals, locale, digits: digitStyle })
                          : "—"}
                        <span className="ms-1 text-[0.6em] font-medium text-muted-foreground">
                          {quote
                            ? locale === "fa"
                              ? quoteCurrencyFa(asset.quoteCurrency)
                              : quoteCurrencyEn(asset.quoteCurrency)
                            : ""}
                        </span>
                      </span>
                      {quote && dashboard.showDailyChange && (
                        <span
                          className={cn(
                            "mt-1 block text-xs font-bold tnum",
                            quote.changePercent >= 0 ? "text-up" : "text-down",
                          )}
                          dir="ltr"
                        >
                          {quote.changePercent >= 0 ? "▲ +" : "▼ "}
                          {formatNumber(quote.changePercent, { decimals: 2, locale, digits: digitStyle })}%
                        </span>
                      )}
                    </button>
                  ) : null,
                )}
          </div>
        </section>
      )}

      {/* movers */}
      {dashboard.sections.movers && !loading && (
        <section>
          <SectionHeader title={t("moversTitle")} />
          <div className="space-y-2">
            {movers.map(({ q, a }) =>
              a ? (
                <AssetRow key={a.id} asset={a} quote={q} onOpen={onOpenAsset} />
              ) : null,
            )}
          </div>
        </section>
      )}

      {/* watchlist preview */}
      {dashboard.sections.watchlist && (
        <section>
          <SectionHeader
            title={t("watchlistPreview")}
            action={
              <button
                className="flex items-center gap-1 text-xs font-semibold text-primary"
                onClick={() => onGoTo("watchlist")}
              >
                <Star className="h-3.5 w-3.5" />
                {t("seeAll")}
              </button>
            }
          />
          {watchQuotes.length === 0 ? (
            <div className="rounded-xl bg-card p-4 text-center text-sm text-muted-foreground">
              {t("watchlistEmptyHint")}
            </div>
          ) : (
            <div className="space-y-2">
              {watchQuotes.map(({ asset, quote }) =>
                asset ? (
                  <AssetRow key={asset.id} asset={asset} quote={quote} onOpen={onOpenAsset} />
                ) : null,
              )}
            </div>
          )}
        </section>
      )}

      {/* quick convert */}
      <button
        className="flex w-full items-center gap-3 rounded-2xl bg-primary px-4 py-3.5 text-start text-primary-foreground transition-opacity hover:opacity-90"
        onClick={() => onGoTo("converter")}
      >
        <ArrowLeftRight className="h-5 w-5" />
        <span className="flex-1 text-sm font-bold">{t("quickConvert")}</span>
        <LineChartIcon className="h-4 w-4 opacity-60" />
      </button>

      {/* dashboard customization dialog */}
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="rounded-2xl">
          <DialogHeader>
            <DialogTitle>{t("editDashboard")}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2.5">
              {(
                [
                  ["featured", t("sectionFeatured")],
                  ["movers", t("sectionMovers")],
                  ["watchlist", t("sectionWatchlist")],
                ] as const
              ).map(([key, label]) => (
                <label key={key} className="flex items-center justify-between text-sm">
                  {label}
                  <Switch
                    checked={dashboard.sections[key]}
                    onCheckedChange={(v) =>
                      setDashboard({ sections: { ...dashboard.sections, [key]: v } })
                    }
                  />
                </label>
              ))}
              <label className="flex items-center justify-between text-sm">
                {t("dailyChange")}
                <Switch
                  checked={dashboard.showDailyChange}
                  onCheckedChange={(v) => setDashboard({ showDailyChange: v })}
                />
              </label>
            </div>

            <div>
              <p className="mb-2 text-sm font-bold">{t("density")}</p>
              <div className="flex gap-2">
                {(["compact", "comfortable"] as const).map((d) => (
                  <Button
                    key={d}
                    size="sm"
                    variant={dashboard.density === d ? "default" : "secondary"}
                    onClick={() => setDashboard({ density: d })}
                  >
                    {t(d)}
                  </Button>
                ))}
              </div>
            </div>

            <div>
              <p className="mb-2 text-sm font-bold">{t("addFeatured")}</p>
              <input
                className="mb-2 h-9 w-full rounded-lg border bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
                placeholder={t("search")}
                value={pickQuery}
                onChange={(e) => setPickQuery(e.target.value)}
              />
              <div className="max-h-56 space-y-1 overflow-y-auto">
                {pickResults.map((a) => {
                  const on = featured.includes(a.id);
                  return (
                    <button
                      key={a.id}
                      className={cn(
                        "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-start text-sm hover:bg-accent",
                        on && "bg-accent",
                      )}
                      onClick={() =>
                        useCheghadr.getState().toggleFeatured(a.id)
                      }
                    >
                      <span aria-hidden>{a.badge}</span>
                      <span className="flex-1 truncate">
                        {locale === "fa" ? a.nameFa : a.nameEn}
                      </span>
                      {on && <span className="text-xs text-primary">✓</span>}
                    </button>
                  );
                })}
              </div>
            </div>

            <Button
              variant="outline"
              className="w-full"
              onClick={() => {
                resetDashboard();
                setEditOpen(false);
              }}
            >
              <RotateCcw className="me-1 h-4 w-4" />
              {t("resetLayout")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function quoteCurrencyFa(ccy: string): string {
  const m: Record<string, string> = { IRT: "تومان", IRR: "ریال", USD: "دلار", "": "امتیاز" };
  return m[ccy] ?? ccy;
}
function quoteCurrencyEn(ccy: string): string {
  const m: Record<string, string> = { IRT: "Toman", IRR: "Rial", USD: "USD", "": "pts" };
  return m[ccy] ?? ccy;
}

export { SkeletonRow };
