"use client";

import React, { useMemo } from "react";
import { Download, Star } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/lib/i18n";
import { useCheghadr } from "@/lib/market/store";
import { getAsset } from "@/lib/market/catalog";
import { useMarkets } from "./use-markets";
import { AssetRow } from "./rows";
import { DemoBanner, OfflineBanner, SkeletonRow } from "./common";
import { toast } from "@/hooks/use-toast";

export function WatchlistView({ onOpenAsset }: { onOpenAsset: (id: string) => void }) {
  const { t, locale } = useI18n();
  const { byId, loading, offline } = useMarkets();
  const favorites = useCheghadr((s) => s.favorites);
  const digitStyle = useCheghadr((s) => s.digitStyle);

  const rows = useMemo(
    () =>
      favorites
        .map((id) => ({ asset: getAsset(id), quote: byId.get(id) }))
        .filter((r) => r.asset),
    [favorites, byId],
  );

  const exportCsv = () => {
    const header = "id,symbol,name,price,quoteCurrency,changePercent,lastUpdated\n";
    const lines = rows
      .map(({ asset, quote }) =>
        [
          asset!.id,
          asset!.symbol,
          locale === "fa" ? asset!.nameFa : asset!.nameEn,
          quote?.price ?? "",
          asset!.quoteCurrency || "pts",
          quote ? quote.changePercent.toFixed(2) : "",
          quote ? new Date(quote.fetchTime).toISOString() : "",
        ].join(","),
      )
      .join("\n");
    const blob = new Blob(["\uFEFF" + header + lines], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "cheghadr-watchlist.csv";
    a.click();
    URL.revokeObjectURL(url);
    toast({ description: t("csvExported") });
  };

  return (
    <div className="space-y-3 pb-2">
      {offline && <OfflineBanner />}
      <DemoBanner />
      <div className="flex items-center justify-between px-1">
        <h1 className="flex items-center gap-2 text-lg font-extrabold">
          <Star className="h-5 w-5 fill-gold text-gold" />
          {t("navWatchlist")}
        </h1>
        {rows.length > 0 && (
          <Button variant="secondary" size="sm" onClick={exportCsv}>
            <Download className="me-1 h-4 w-4" />
            {t("exportCsv")}
          </Button>
        )}
      </div>

      {loading ? (
        Array.from({ length: 4 }).map((_, i) => <SkeletonRow key={i} />)
      ) : rows.length === 0 ? (
        <div className="rounded-2xl bg-card p-8 text-center">
          <p className="font-bold">{t("watchlistEmpty")}</p>
          <p className="mt-1 text-sm text-muted-foreground">{t("watchlistEmptyHint")}</p>
        </div>
      ) : (
        <div className="space-y-2">
          {rows.map(({ asset, quote }) =>
            asset ? (
              <AssetRow
                key={asset.id}
                asset={asset}
                quote={quote}
                onOpen={onOpenAsset}
              />
            ) : null,
          )}
        </div>
      )}
    </div>
  );
}
