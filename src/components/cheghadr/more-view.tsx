"use client";

import React, { useMemo, useState } from "react";
import {
  Bell,
  BellRing,
  Database,
  Info,
  Monitor,
  Moon,
  Plus,
  Sun,
  Trash2,
} from "lucide-react";
import { useTheme } from "next-themes";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useToast } from "@/hooks/use-toast";
import { useI18n } from "@/lib/i18n";
import { useCheghadr, type AlertType } from "@/lib/market/store";
import { getAsset } from "@/lib/market/catalog";
import { useMarkets } from "./use-markets";
import { DemoBanner } from "./common";
import { formatNumber, quoteCurrencyLabel, formatRelative } from "@/lib/market/format";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

export function MoreView({ onOpenAsset }: { onOpenAsset: (id: string) => void }) {
  const { t, locale } = useI18n();
  const { theme, setTheme } = useTheme();
  const { toast } = useToast();
  const { byId, lastUpdate } = useMarkets();
  const s = useCheghadr();
  const [tab, setTab] = useState<"settings" | "alerts" | "portfolio" | "about">("settings");

  // portfolio form
  const [pfAsset, setPfAsset] = useState("btc");
  const [pfQty, setPfQty] = useState("");
  const [pfCost, setPfCost] = useState("");

  const enableNotifications = async () => {
    if (typeof window === "undefined" || !("Notification" in window)) return;
    const p = await Notification.requestPermission();
    toast({
      description: p === "granted" ? t("notificationsOn") : t("notificationsDenied"),
    });
  };

  const holdings = useMemo(
    () =>
      s.holdings
        .map((h) => ({ h, asset: getAsset(h.assetId), quote: byId.get(h.assetId) }))
        .filter((r) => r.asset),
    [s.holdings, byId],
  );
  const totalValue = holdings.reduce((sum, r) => sum + (r.quote?.price ?? 0) * r.h.quantity, 0);
  const totalCost = holdings.reduce((sum, r) => sum + r.h.avgCost * r.h.quantity, 0);

  return (
    <div className="space-y-3 pb-2">
      <DemoBanner />
      <h1 className="px-1 text-lg font-extrabold">{t("settings")}</h1>

      <Tabs value={tab} onValueChange={(v) => setTab(v as typeof tab)}>
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="settings">{t("settings")}</TabsTrigger>
          <TabsTrigger value="alerts">{t("alerts")}</TabsTrigger>
          <TabsTrigger value="portfolio">{t("portfolio")}</TabsTrigger>
          <TabsTrigger value="about">{t("about")}</TabsTrigger>
        </TabsList>
      </Tabs>

      {tab === "settings" && (
        <div className="space-y-4 rounded-2xl bg-card p-4">
          {/* language */}
          <div className="flex items-center justify-between">
            <span className="text-sm font-bold">{t("language")}</span>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant={s.locale === "fa" ? "default" : "secondary"}
                onClick={() => s.setLocale("fa")}
              >
                {t("persian")}
              </Button>
              <Button
                size="sm"
                variant={s.locale === "en" ? "default" : "secondary"}
                onClick={() => s.setLocale("en")}
              >
                {t("english")}
              </Button>
            </div>
          </div>

          {/* theme */}
          <div className="flex items-center justify-between">
            <span className="text-sm font-bold">{t("appearance")}</span>
            <div className="flex gap-1.5">
              {(
                [
                  ["light", Sun, t("themeLight")],
                  ["dark", Moon, t("themeDark")],
                  ["system", Monitor, t("themeSystem")],
                ] as const
              ).map(([v, Icon, label]) => (
                <Button
                  key={v}
                  size="icon"
                  variant={theme === v ? "default" : "secondary"}
                  className="h-9 w-9"
                  aria-label={label}
                  onClick={() => setTheme(v)}
                >
                  <Icon className="h-4 w-4" />
                </Button>
              ))}
            </div>
          </div>

          {/* digits */}
          <div className="flex items-center justify-between">
            <span className="text-sm font-bold">{t("digits")}</span>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant={s.digitStyle === "persian" ? "default" : "secondary"}
                onClick={() => s.setDigitStyle("persian")}
              >
                {t("digitsPersian")}
              </Button>
              <Button
                size="sm"
                variant={s.digitStyle === "latin" ? "default" : "secondary"}
                onClick={() => s.setDigitStyle("latin")}
              >
                {t("digitsLatin")}
              </Button>
            </div>
          </div>

          {/* calendar */}
          <div className="flex items-center justify-between">
            <span className="text-sm font-bold">{t("calendar")}</span>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant={s.calendar === "jalali" ? "default" : "secondary"}
                onClick={() => s.setCalendar("jalali")}
              >
                {t("jalali")}
              </Button>
              <Button
                size="sm"
                variant={s.calendar === "gregorian" ? "default" : "secondary"}
                onClick={() => s.setCalendar("gregorian")}
              >
                {t("gregorian")}
              </Button>
            </div>
          </div>

          {/* refresh interval */}
          <div className="flex items-center justify-between">
            <span className="text-sm font-bold">{t("refreshInterval")}</span>
            <div className="flex gap-1.5">
              {([1, 2, 5, 0] as const).map((m) => (
                <Button
                  key={m}
                  size="sm"
                  variant={s.refreshIntervalMinutes === m ? "default" : "secondary"}
                  onClick={() => s.setRefreshInterval(m)}
                >
                  {m === 0 ? t("off") : t(m === 1 ? "min1" : m === 2 ? "min2" : "min5")}
                </Button>
              ))}
            </div>
          </div>

          {/* quiet hours */}
          <label className="flex items-center justify-between">
            <span className="text-sm font-bold">{t("alertQuietHours")}</span>
            <Switch checked={s.quietHours} onCheckedChange={s.setQuietHours} />
          </label>

          {/* notifications */}
          <Button variant="secondary" className="w-full" onClick={enableNotifications}>
            <BellRing className="me-1 h-4 w-4" />
            {t("enableNotifications")}
          </Button>

          {/* cache */}
          <Button
            variant="outline"
            className="w-full"
            onClick={() => {
              s.clearCache();
              toast({ description: t("cacheCleared") });
            }}
          >
            <Trash2 className="me-1 h-4 w-4" />
            {t("clearCache")}
          </Button>
        </div>
      )}

      {tab === "alerts" && (
        <div className="space-y-2">
          <p className="px-1 text-xs text-muted-foreground">{t("alertLocalNote")}</p>
          {s.alerts.length === 0 ? (
            <div className="rounded-2xl bg-card p-8 text-center">
              <Bell className="mx-auto mb-2 h-8 w-8 text-muted-foreground" />
              <p className="font-bold">{t("noAlerts")}</p>
              <p className="mt-1 text-sm text-muted-foreground">{t("noAlertsHint")}</p>
            </div>
          ) : (
            s.alerts.map((a) => {
              const asset = getAsset(a.assetId);
              if (!asset) return null;
              const label = t(
                a.type === "above"
                  ? "alertAbove"
                  : a.type === "below"
                    ? "alertBelow"
                    : a.type === "pctUp"
                      ? "alertPctUp"
                      : "alertPctDown",
                { n: formatNumber(a.value, { decimals: a.type.startsWith("pct") ? 1 : asset.decimals, locale, digits: s.digitStyle }) },
              );
              return (
                <div key={a.id} className="flex items-center gap-3 rounded-xl bg-card p-3">
                  <span aria-hidden>{asset.badge}</span>
                  <div className="min-w-0 flex-1">
                    <button
                      className="block truncate text-sm font-semibold hover:underline"
                      onClick={() => onOpenAsset(asset.id)}
                    >
                      {locale === "fa" ? asset.nameFa : asset.nameEn}
                    </button>
                    <p className="text-xs text-muted-foreground tnum" dir="ltr">
                      {label}
                      {a.lastTriggeredAt &&
                        ` • ${t("alertTriggered")}: ${formatRelative(a.lastTriggeredAt, Date.now(), locale, s.digitStyle)}`}
                    </p>
                  </div>
                  <span
                    className={cn(
                      "rounded px-1.5 py-0.5 text-[10px] font-bold",
                      !a.enabled
                        ? "bg-muted text-muted-foreground"
                        : a.armed
                          ? "bg-up/10 text-up"
                          : "bg-gold/15 text-gold",
                    )}
                  >
                    {a.enabled ? (a.armed ? t("alertActive") : t("alertTriggered")) : t("off")}
                  </span>
                  <button
                    className="rounded-full p-1.5 text-muted-foreground hover:text-down"
                    onClick={() => s.removeAlert(a.id)}
                    aria-label={t("deleteAlert")}
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              );
            })
          )}
        </div>
      )}

      {tab === "portfolio" && (
        <div className="space-y-3">
          <div className="rounded-2xl bg-card p-4">
            <p className="mb-2 text-sm font-bold">{t("addHolding")}</p>
            <div className="grid grid-cols-3 gap-2">
              <Select value={pfAsset} onValueChange={setPfAsset}>
                <SelectTrigger className="col-span-3 h-9" aria-label="asset">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="max-h-64">
                  {["btc", "eth", "usdt", "gold-18k", "coin-emami", "usd-irt", "tse-foolad"].map((id) => {
                    const a = getAsset(id)!;
                    return (
                      <SelectItem key={id} value={id}>
                        {a.badge} {locale === "fa" ? a.nameFa : a.nameEn}
                      </SelectItem>
                    );
                  })}
                </SelectContent>
              </Select>
              <Input
                type="number"
                dir="ltr"
                placeholder={t("quantity")}
                value={pfQty}
                onChange={(e) => setPfQty(e.target.value)}
                className="h-9"
              />
              <Input
                type="number"
                dir="ltr"
                placeholder={t("avgCost")}
                value={pfCost}
                onChange={(e) => setPfCost(e.target.value)}
                className="h-9"
              />
              <Button
                size="sm"
                className="h-9"
                onClick={() => {
                  const q = parseFloat(pfQty);
                  const c = parseFloat(pfCost);
                  if (q > 0 && c >= 0) {
                    s.addHolding(pfAsset, q, c);
                    setPfQty("");
                    setPfCost("");
                  }
                }}
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>
          </div>

          {holdings.length === 0 ? (
            <p className="rounded-2xl bg-card p-8 text-center text-sm text-muted-foreground">
              {t("portfolioEmpty")}
            </p>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-2">
                <div className="rounded-xl bg-card p-3">
                  <p className="text-xs text-muted-foreground">{t("currentValue")}</p>
                  <p className="mt-1 font-extrabold tnum" dir="ltr">
                    {formatNumber(totalValue, { decimals: 0, locale, digits: s.digitStyle })}
                  </p>
                </div>
                <div className="rounded-xl bg-card p-3">
                  <p className="text-xs text-muted-foreground">{t("unrealized")}</p>
                  <p
                    className={cn(
                      "mt-1 font-extrabold tnum",
                      totalValue - totalCost >= 0 ? "text-up" : "text-down",
                    )}
                    dir="ltr"
                  >
                    {formatNumber(totalValue - totalCost, {
                      decimals: 0,
                      locale,
                      digits: s.digitStyle,
                      signed: true,
                    })}
                  </p>
                </div>
              </div>
              {holdings.map(({ h, asset, quote }) => {
                const value = (quote?.price ?? 0) * h.quantity;
                const pl = value - h.avgCost * h.quantity;
                return (
                  <div key={h.id} className="flex items-center gap-3 rounded-xl bg-card p-3">
                    <span aria-hidden>{asset!.badge}</span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">
                        {locale === "fa" ? asset!.nameFa : asset!.nameEn}
                      </p>
                      <p className="text-xs text-muted-foreground tnum" dir="ltr">
                        ×{formatNumber(h.quantity, { decimals: 4, locale, digits: s.digitStyle })} @{" "}
                        {formatNumber(h.avgCost, { decimals: 0, locale, digits: s.digitStyle })}
                      </p>
                    </div>
                    <div className="text-end">
                      <p className="text-sm font-bold tnum" dir="ltr">
                        {formatNumber(value, { decimals: 0, locale, digits: s.digitStyle })}
                      </p>
                      <p className={cn("text-xs font-bold tnum", pl >= 0 ? "text-up" : "text-down")} dir="ltr">
                        {formatNumber(pl, { decimals: 0, locale, digits: s.digitStyle, signed: true })}
                      </p>
                    </div>
                    <button
                      className="rounded-full p-1.5 text-muted-foreground hover:text-down"
                      onClick={() => s.removeHolding(h.id)}
                      aria-label={t("removeHolding")}
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                );
              })}
              <p className="px-1 text-[11px] text-muted-foreground">
                {t("portfolioDisclaimer")}
              </p>
            </>
          )}
        </div>
      )}

      {tab === "about" && (
        <div className="space-y-3 rounded-2xl bg-card p-4">
          <div className="flex items-center gap-3">
            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-primary text-xl font-black text-primary-foreground">
              چ
            </span>
            <div>
              <p className="text-base font-extrabold">{t("appName")}</p>
              <p className="text-xs text-muted-foreground">{t("tagline")}</p>
            </div>
          </div>
          <p className="text-sm leading-6 text-muted-foreground">{t("aboutBody")}</p>
          <div className="rounded-xl bg-secondary p-3">
            <p className="flex items-center gap-1.5 text-sm font-bold">
              <Database className="h-4 w-4" />
              {t("aboutData")}
            </p>
            <p className="mt-1.5 text-xs leading-5 text-muted-foreground">
              {t("aboutDataBody")}
            </p>
            <p className="mt-2 text-[11px] text-muted-foreground">
              {t("lastUpdate")}:{" "}
              {lastUpdate ? formatRelative(lastUpdate, Date.now(), locale, s.digitStyle) : "—"}
            </p>
          </div>
          <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Info className="h-3.5 w-3.5" />
            CoinGecko attribution — data provided by coingecko.com
          </p>
        </div>
      )}
    </div>
  );
}

export type { AlertType };
