"use client";

import React, { useEffect, useState } from "react";
import {
  ArrowLeftRight,
  Home,
  LayoutGrid,
  MoreHorizontal,
  Search,
  Star,
} from "lucide-react";
import { useI18n, I18nProvider } from "@/lib/i18n";
import { useCheghadr } from "@/lib/market/store";
import { MarketsProvider } from "./use-markets";
import { HomeView } from "./home-view";
import { MarketsView } from "./markets-view";
import { WatchlistView } from "./watchlist-view";
import { ConverterView } from "./converter-view";
import { MoreView } from "./more-view";
import { AssetDetail } from "./asset-detail";
import { SearchDialog } from "./search-dialog";
import { cn } from "@/lib/utils";

type Tab = "home" | "markets" | "watchlist" | "converter" | "more";

function ShellInner() {
  const { t, locale } = useI18n();
  const storeLocale = useCheghadr((s) => s.locale);
  const [tab, setTab] = useState<Tab>("home");
  const [detailId, setDetailId] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);

  // keep <html> lang/dir in sync with the selected locale (fa=RTL default)
  useEffect(() => {
    document.documentElement.lang = storeLocale;
    document.documentElement.dir = storeLocale === "fa" ? "rtl" : "ltr";
  }, [storeLocale]);

  const openAsset = (id: string) => {
    setDetailId(id);
    setDetailOpen(true);
  };

  const NAV: { id: Tab; label: string; icon: React.ElementType }[] = [
    { id: "home", label: t("navHome"), icon: Home },
    { id: "markets", label: t("navMarkets"), icon: LayoutGrid },
    { id: "watchlist", label: t("navWatchlist"), icon: Star },
    { id: "converter", label: t("navConverter"), icon: ArrowLeftRight },
    { id: "more", label: t("navMore"), icon: MoreHorizontal },
  ];

  return (
    <div
      dir={storeLocale === "fa" ? "rtl" : "ltr"}
      className="mx-auto flex min-h-dvh w-full max-w-lg flex-col bg-background"
    >
      {/* top bar */}
      <header className="sticky top-0 z-30 flex items-center gap-2 border-b bg-background/95 px-4 py-3 backdrop-blur">
        <span className="grid h-8 w-8 place-items-center rounded-xl bg-primary text-base font-black text-primary-foreground">
          چ
        </span>
        <div className="flex-1">
          <p className="text-sm font-extrabold leading-4">{t("appName")}</p>
          <p className="text-[10px] text-muted-foreground">{t("tagline")}</p>
        </div>
        <button
          className="grid h-9 w-9 place-items-center rounded-full bg-secondary text-secondary-foreground hover:bg-accent"
          onClick={() => setSearchOpen(true)}
          aria-label={t("search")}
        >
          <Search className="h-4.5 w-4.5" />
        </button>
      </header>

      {/* views */}
      <main className="flex-1 px-3 pt-3 pb-24">
        <MarketsProvider>
          {tab === "home" && (
            <HomeView onOpenAsset={openAsset} onGoTo={(x) => setTab(x)} />
          )}
          {tab === "markets" && <MarketsView onOpenAsset={openAsset} />}
          {tab === "watchlist" && <WatchlistView onOpenAsset={openAsset} />}
          {tab === "converter" && <ConverterView />}
          {tab === "more" && <MoreView onOpenAsset={openAsset} />}

          <AssetDetail
            assetId={detailId}
            open={detailOpen}
            onOpenChange={setDetailOpen}
          />
          <SearchDialog
            open={searchOpen}
            onOpenChange={setSearchOpen}
            onPick={openAsset}
          />
        </MarketsProvider>
      </main>

      {/* bottom navigation — ~5 primary destinations (blueprint §5) */}
      <nav
        className="fixed inset-x-0 bottom-0 z-30 mx-auto w-full max-w-lg border-t bg-background/95 pb-[env(safe-area-inset-bottom)] backdrop-blur"
        aria-label="primary"
      >
        <div className="grid grid-cols-5">
          {NAV.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              className={cn(
                "flex flex-col items-center gap-0.5 py-2.5 text-[10px] font-semibold transition-colors",
                tab === id ? "text-primary" : "text-muted-foreground hover:text-foreground",
              )}
              onClick={() => setTab(id)}
              aria-current={tab === id ? "page" : undefined}
            >
              <Icon className={cn("h-5 w-5", tab === id && "drop-shadow-sm")} />
              {label}
            </button>
          ))}
        </div>
      </nav>
    </div>
  );
}

export function AppShell() {
  const storeLocale = useCheghadr((s) => s.locale);
  return (
    <I18nProvider locale={storeLocale}>
      <ShellInner />
    </I18nProvider>
  );
}
