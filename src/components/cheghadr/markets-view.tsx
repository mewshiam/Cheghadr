"use client";

import React, { useMemo, useState } from "react";
import { ArrowDownUp, RefreshCw, Star } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useI18n, type StringKey } from "@/lib/i18n";
import { assetsByCategory, getAsset } from "@/lib/market/catalog";
import type { AssetCategory } from "@/lib/market/types";
import { useMarkets } from "./use-markets";
import { AssetRow, } from "./rows";
import { DemoBanner, OfflineBanner, SkeletonRow } from "./common";
import { cn } from "@/lib/utils";

type SortKey = "name" | "price" | "changeAbs" | "changePct";
type FilterKey = "all" | "gainers" | "losers" | "favorites";

const CAT_KEY: Record<AssetCategory, StringKey> = {
  currency: "catCurrency",
  gold: "catGold",
  coin: "catCoin",
  crypto: "catCrypto",
  stock: "catStock",
  commodity: "catCommodity",
};

export function MarketsView({ onOpenAsset }: { onOpenAsset: (id: string) => void }) {
  const { t, locale } = useI18n();
  const { byId, loading, offline, refresh, refreshing } = useMarkets();
  const favorites = useCheghadrSafeFavorites();
  const [cat, setCat] = useState<AssetCategory>("currency");
  const [sortKey, setSortKey] = useState<SortKey>("changePct");
  const [filter, setFilter] = useState<FilterKey>("all");
  const [query, setQuery] = useState("");

  const assets = useMemo(() => assetsByCategory(cat), [cat]);

  const rows = useMemo(() => {
    let list = assets.map((a) => ({ asset: a, quote: byId.get(a.id) }));
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      list = list.filter(
        ({ asset }) =>
          asset.symbol.toLowerCase().includes(q) ||
          asset.nameEn.toLowerCase().includes(q) ||
          asset.nameFa.includes(query.trim()) ||
          asset.aliases.some((x) => x.toLowerCase().includes(q)),
      );
    }
    if (filter === "gainers") list = list.filter((r) => (r.quote?.changePercent ?? 0) > 0);
    if (filter === "losers") list = list.filter((r) => (r.quote?.changePercent ?? 0) < 0);
    if (filter === "favorites") list = list.filter((r) => favorites.includes(r.asset.id));
    const dir = locale === "fa" ? 1 : -1;
    list.sort((a, b) => {
      switch (sortKey) {
        case "name":
          return dir * (locale === "fa"
            ? a.asset.nameFa.localeCompare(b.asset.nameFa, "fa")
            : a.asset.nameEn.localeCompare(b.asset.nameEn));
        case "price":
          return (b.quote?.price ?? 0) - (a.quote?.price ?? 0);
        case "changeAbs":
          return (b.quote?.changeAbsolute ?? 0) - (a.quote?.changeAbsolute ?? 0);
        default:
          return (b.quote?.changePercent ?? 0) - (a.quote?.changePercent ?? 0);
      }
    });
    return list;
  }, [assets, byId, query, filter, favorites, sortKey, locale]);

  const SORT_LABELS: Record<SortKey, StringKey> = {
    name: "sortName",
    price: "sortPrice",
    changeAbs: "sortChangeAbs",
    changePct: "sortChangePct",
  };

  return (
    <div className="space-y-3 pb-2">
      {offline && <OfflineBanner />}
      <DemoBanner />

      <Tabs value={cat} onValueChange={(v) => setCat(v as AssetCategory)}>
        <TabsList className="flex h-10 w-full justify-start overflow-x-auto bg-muted/60">
          {(Object.keys(CAT_KEY) as AssetCategory[]).map((c) => (
            <TabsTrigger key={c} value={c} className="px-3 text-xs font-semibold">
              {t(CAT_KEY[c])}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <div className="flex items-center gap-2">
        <input
          className="h-10 flex-1 rounded-xl border bg-card px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
          placeholder={t("search")}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label={t("search")}
        />
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="secondary" size="icon" className="h-10 w-10" aria-label={t("sort")}>
              <ArrowDownUp className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {(Object.keys(SORT_LABELS) as SortKey[]).map((k) => (
              <DropdownMenuItem
                key={k}
                onClick={() => setSortKey(k)}
                className={cn(sortKey === k && "bg-accent")}
              >
                {t(SORT_LABELS[k])}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
        <Button
          variant="secondary"
          size="icon"
          className="h-10 w-10"
          onClick={() => void refresh()}
          disabled={refreshing}
          aria-label={t("refresh")}
        >
          <RefreshCw className={cn("h-4 w-4", refreshing && "animate-spin")} />
        </Button>
      </div>

      <div className="flex gap-2">
        {(
          [
            ["all", t("filterAll")],
            ["gainers", t("filterGainers")],
            ["losers", t("filterLosers")],
            ["favorites", t("filterFavorites")],
          ] as [FilterKey, string][]
        ).map(([k, label]) => (
          <button
            key={k}
            className={cn(
              "flex items-center gap-1 rounded-full px-3 py-1.5 text-xs font-semibold transition-colors",
              filter === k
                ? "bg-primary text-primary-foreground"
                : "bg-secondary text-secondary-foreground",
            )}
            onClick={() => setFilter(k)}
          >
            {k === "favorites" && <Star className="h-3 w-3" />}
            {label}
          </button>
        ))}
      </div>

      <div className="space-y-2">
        {loading ? (
          Array.from({ length: 8 }).map((_, i) => <SkeletonRow key={i} />)
        ) : rows.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted-foreground">
            {t("noResults")}
          </p>
        ) : (
          rows.map(({ asset, quote }) => (
            <AssetRow key={asset.id} asset={asset} quote={quote} onOpen={onOpenAsset} />
          ))
        )}
      </div>
    </div>
  );
}

import { useCheghadr } from "@/lib/market/store";
function useCheghadrSafeFavorites() {
  return useCheghadr((s) => s.favorites);
}

export { getAsset };
