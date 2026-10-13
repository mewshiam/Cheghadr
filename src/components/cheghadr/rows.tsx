"use client";

import React from "react";
import { Star } from "lucide-react";
import type { Asset, Quote } from "@/lib/market/types";
import { useCheghadr } from "@/lib/market/store";
import { useI18n } from "@/lib/i18n";
import { cn } from "@/lib/utils";
import { AssetAvatar, ChangeBadge, FreshnessBadge, PriceText } from "./common";
import { Sparkline } from "./charts";
import type { PricePoint } from "@/lib/market/types";

export function AssetRow({
  asset,
  quote,
  onOpen,
  sparkline,
  showChange = true,
}: {
  asset: Asset;
  quote?: Quote;
  onOpen: (id: string) => void;
  sparkline?: PricePoint[];
  showChange?: boolean;
}) {
  const { locale } = useI18n();
  const digitStyle = useCheghadr((s) => s.digitStyle);
  const favorites = useCheghadr((s) => s.favorites);
  const toggleFavorite = useCheghadr((s) => s.toggleFavorite);
  const isFav = favorites.includes(asset.id);

  return (
    <div
      className={cn(
        "group flex cursor-pointer items-center gap-3 rounded-xl bg-card p-3 transition-colors hover:bg-accent",
      )}
      onClick={() => onOpen(asset.id)}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === "Enter" && onOpen(asset.id)}
      aria-label={locale === "fa" ? asset.nameFa : asset.nameEn}
    >
      <AssetAvatar asset={asset} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold leading-5">
          {locale === "fa" ? asset.nameFa : asset.nameEn}
        </p>
        <p className="flex items-center gap-1.5 truncate text-xs text-muted-foreground" dir="ltr">
          {asset.symbol}
          {quote && <FreshnessBadge freshness={quote.freshness} />}
        </p>
      </div>
      {sparkline && sparkline.length > 1 && (
        <Sparkline points={sparkline} up={(quote?.changePercent ?? 0) >= 0} />
      )}
      <div className="flex flex-col items-end gap-1">
        <PriceText
          value={quote?.price ?? NaN}
          asset={asset}
          digits={digitStyle}
          className="text-sm"
        />
        {showChange && quote && (
          <ChangeBadge quote={quote} digits={digitStyle} compact />
        )}
      </div>
      <button
        className="shrink-0 rounded-full p-1.5 text-muted-foreground transition-colors hover:text-gold"
        onClick={(e) => {
          e.stopPropagation();
          toggleFavorite(asset.id);
        }}
        aria-label={isFav ? "unstar" : "star"}
      >
        <Star
          className={cn(
            "h-4.5 w-4.5",
            isFav && "fill-gold text-gold",
          )}
        />
      </button>
    </div>
  );
}
