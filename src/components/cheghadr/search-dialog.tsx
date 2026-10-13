"use client";

import React, { useMemo, useState } from "react";
import { Clock, Search, X } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { useI18n } from "@/lib/i18n";
import { useCheghadr } from "@/lib/market/store";
import { searchAssets } from "@/lib/market/catalog";
import type { Asset } from "@/lib/market/types";
import { AssetAvatar } from "./common";

export function SearchDialog({
  open,
  onOpenChange,
  onPick,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  onPick: (assetId: string) => void;
}) {
  const { t, locale } = useI18n();
  const [q, setQ] = useState("");
  const searchHistory = useCheghadr((s) => s.searchHistory);
  const pushSearch = useCheghadr((s) => s.pushSearch);
  const clearSearchHistory = useCheghadr((s) => s.clearSearchHistory);

  const results = useMemo(() => searchAssets(q), [q]);

  const pick = (a: Asset) => {
    pushSearch(locale === "fa" ? a.nameFa : a.nameEn);
    setQ("");
    onOpenChange(false);
    onPick(a.id);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="top-16 translate-y-0 gap-0 rounded-2xl p-0 sm:top-20">
        <DialogHeader className="sr-only">
          <DialogTitle>{t("search")}</DialogTitle>
        </DialogHeader>
        <div className="flex items-center gap-2 border-b px-3 py-2.5">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
          <Input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={t("search")}
            className="h-9 border-0 bg-transparent px-0 shadow-none focus-visible:ring-0"
            aria-label={t("search")}
          />
          {q && (
            <button onClick={() => setQ("")} aria-label="clear">
              <X className="h-4 w-4 text-muted-foreground" />
            </button>
          )}
        </div>
        <div className="max-h-[55dvh] overflow-y-auto p-2">
          {!q && searchHistory.length > 0 && (
            <div className="mb-1">
              <div className="flex items-center justify-between px-2 py-1.5">
                <span className="text-xs font-bold text-muted-foreground">
                  {t("searchHistory")}
                </span>
                <button
                  className="text-xs text-muted-foreground hover:text-foreground"
                  onClick={clearSearchHistory}
                >
                  {t("clearHistory")}
                </button>
              </div>
              {searchHistory.map((h) => (
                <button
                  key={h}
                  className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-start text-sm hover:bg-accent"
                  onClick={() => setQ(h)}
                >
                  <Clock className="h-4 w-4 text-muted-foreground" />
                  {h}
                </button>
              ))}
            </div>
          )}
          {q && results.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">
              {t("noResults")}
            </p>
          )}
          {(q ? results : []).map((a) => (
            <button
              key={a.id}
              className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-start hover:bg-accent"
              onClick={() => pick(a)}
            >
              <AssetAvatar asset={a} size={34} />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold">
                  {locale === "fa" ? a.nameFa : a.nameEn}
                </span>
                <span className="block truncate text-xs text-muted-foreground" dir="ltr">
                  {a.symbol}
                </span>
              </span>
            </button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
