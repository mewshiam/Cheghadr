"use client";

import React from "react";
import { ArrowDown, ArrowUp, Minus, ShieldAlert } from "lucide-react";
import type { Asset, Freshness, Quote } from "@/lib/market/types";
import { formatNumber, quoteCurrencyLabel, type DigitStyle } from "@/lib/market/format";
import { useI18n } from "@/lib/i18n";
import { cn } from "@/lib/utils";

/** Change indicator — color + arrow/sign so color is not the only cue (blueprint §5). */
export function ChangeBadge({
  quote,
  digits,
  className,
  compact = false,
}: {
  quote: Pick<Quote, "changePercent" | "changeAbsolute">;
  digits: DigitStyle;
  className?: string;
  compact?: boolean;
}) {
  const { locale } = useI18n();
  const pct = quote.changePercent;
  const up = pct > 0.005;
  const down = pct < -0.005;
  const Icon = up ? ArrowUp : down ? ArrowDown : Minus;
  const color = up ? "text-up" : down ? "text-down" : "text-muted-foreground";
  const bg = up
    ? "bg-up/10"
    : down
      ? "bg-down/10"
      : "bg-muted";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-0.5 rounded-md px-1.5 py-0.5 text-xs font-semibold tnum",
        color,
        bg,
        className,
      )}
      dir="ltr"
    >
      <Icon className="h-3 w-3" aria-hidden />
      {formatNumber(Math.abs(pct), { decimals: 2, digits, locale })}
      {"%"}
      {!compact && (
        <span className="opacity-70">
          ({formatNumber(quote.changeAbsolute, { decimals: 0, digits, locale, signed: up })})
        </span>
      )}
    </span>
  );
}

export function PriceText({
  value,
  asset,
  digits,
  className,
}: {
  value: number;
  asset: Pick<Asset, "decimals" | "quoteCurrency">;
  digits: DigitStyle;
  className?: string;
}) {
  const { locale } = useI18n();
  return (
    <span className={cn("tnum font-bold", className)} dir="ltr">
      {formatNumber(value, { decimals: asset.decimals, digits, locale })}
      <span className="ms-1 text-[0.62em] font-medium text-muted-foreground">
        {quoteCurrencyLabel(asset.quoteCurrency, locale)}
      </span>
    </span>
  );
}

export function AssetAvatar({ asset, size = 40 }: { asset: Asset; size?: number }) {
  return (
    <span
      className="grid shrink-0 place-items-center rounded-full bg-secondary text-lg leading-none"
      style={{ width: size, height: size }}
      aria-hidden
    >
      {asset.badge}
    </span>
  );
}

export function FreshnessBadge({ freshness }: { freshness: Freshness }) {
  const { t } = useI18n();
  const map: Record<Freshness, { label: string; cls: string } | null> = {
    live: { label: t("liveBadge"), cls: "bg-up/10 text-up" },
    demo: { label: t("demoBadge"), cls: "bg-gold/15 text-gold" },
    delayed: { label: t("demoBadge"), cls: "bg-gold/15 text-gold" },
    stale: { label: t("staleBadge"), cls: "bg-down/10 text-down" },
    unavailable: null,
  };
  const m = map[freshness];
  if (!m) return null;
  return (
    <span className={cn("rounded px-1.5 py-0.5 text-[10px] font-bold", m.cls)}>
      {m.label}
    </span>
  );
}

/** Persistent honesty banner (blueprint §2 — never present demo data as real). */
export function DemoBanner() {
  const { t } = useI18n();
  return (
    <div className="mx-3 mt-2 flex items-start gap-2 rounded-xl border border-gold/40 bg-gold/10 px-3 py-2 text-xs leading-5 text-foreground">
      <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-gold" aria-hidden />
      <p>{t("demoBanner")}</p>
    </div>
  );
}

export function OfflineBanner() {
  const { t } = useI18n();
  return (
    <div className="mx-3 mt-2 rounded-xl border border-down/40 bg-down/10 px-3 py-2 text-xs font-medium text-foreground">
      {t("offline")}
    </div>
  );
}

export function SectionHeader({
  title,
  action,
}: {
  title: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="mb-2 flex items-center justify-between px-1">
      <h2 className="text-sm font-bold text-muted-foreground">{title}</h2>
      {action}
    </div>
  );
}

export function SkeletonRow() {
  return (
    <div className="flex items-center gap-3 rounded-xl bg-card p-3">
      <div className="h-10 w-10 rounded-full bg-muted skeleton-shimmer" />
      <div className="flex-1 space-y-2">
        <div className="h-3.5 w-24 rounded bg-muted skeleton-shimmer" />
        <div className="h-3 w-16 rounded bg-muted skeleton-shimmer" />
      </div>
      <div className="h-4 w-20 rounded bg-muted skeleton-shimmer" />
    </div>
  );
}
