"use client";

import React, { useMemo, useState } from "react";
import { ArrowLeftRight, Calculator } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useI18n } from "@/lib/i18n";
import { useCheghadr } from "@/lib/market/store";
import { ASSETS, assetsByCategory, getAsset } from "@/lib/market/catalog";
import {
  convertCurrency,
  goldRetailEstimate,
  GRAMS_PER_MITHQAL,
  GRAMS_PER_OUNCE,
  type WeightUnit,
} from "@/lib/market/calc";
import { formatNumber, quoteCurrencyLabel } from "@/lib/market/format";
import { useMarkets } from "./use-markets";
import { DemoBanner } from "./common";

function AssetSelect({
  value,
  onChange,
  ids,
  label,
}: {
  value: string;
  onChange: (v: string) => void;
  ids: string[];
  label: string;
}) {
  const { locale } = useI18n();
  return (
    <select
      aria-label={label}
      className="h-10 w-full rounded-xl border bg-card px-2 text-sm font-semibold outline-none focus:ring-2 focus:ring-ring"
      value={value}
      onChange={(e) => onChange(e.target.value)}
    >
      {ids.map((id) => {
        const a = getAsset(id);
        if (!a) return null;
        return (
          <option key={id} value={id}>
            {locale === "fa" ? a.nameFa : a.nameEn} — {a.symbol}
          </option>
        );
      })}
    </select>
  );
}

export function ConverterView() {
  const { t, locale } = useI18n();
  const { byId, lastUpdate, offline } = useMarkets();
  const digitStyle = useCheghadr((s) => s.digitStyle);
  const calendar = useCheghadr((s) => s.calendar);
  const pushPair = useCheghadr((s) => s.pushPair);
  const recentPairs = useCheghadr((s) => s.recentPairs);

  const currencyIds = useMemo(
    () => assetsByCategory("currency").map((a) => a.id),
    [],
  );
  const [amount, setAmount] = useState("1");
  const [fromId, setFromId] = useState("usd-irt");
  const [toId, setToId] = useState("eur-irt");

  // gold calc
  const gram24 = byId.get("gold-24k")?.price ?? 0;
  const [mode, setMode] = useState<"currency" | "gold">("currency");
  const [weight, setWeight] = useState("1");
  const [unit, setUnit] = useState<WeightUnit>("gram");
  const [karat, setKarat] = useState(18);
  const [making, setMaking] = useState("7");
  const [profit, setProfit] = useState("4");
  const [vat, setVat] = useState("9");

  const fromAsset = getAsset(fromId);
  const toAsset = getAsset(toId);
  const fromRate = byId.get(fromId)?.price;
  const toRate = byId.get(toId)?.price;
  const nAmount = parseFloat(amount.replace(/,/g, "")) || 0;
  const converted =
    fromRate && toRate ? convertCurrency(nAmount, fromRate, toRate) : NaN;

  const gold = goldRetailEstimate({
    weight: parseFloat(weight) || 0,
    unit,
    karat,
    gram24Price: gram24,
    makingChargePct: parseFloat(making) || 0,
    profitPct: parseFloat(profit) || 0,
    vatPct: parseFloat(vat) || 0,
  });

  const fmt = (v: number, d = 2) =>
    formatNumber(Number.isFinite(v) ? v : 0, { decimals: d, locale, digits: digitStyle });

  const swap = () => {
    setFromId(toId);
    setToId(fromId);
    pushPair(toId, fromId);
  };

  return (
    <div className="space-y-3 pb-2">
      <DemoBanner />
      <h1 className="px-1 text-lg font-extrabold">{t("navConverter")}</h1>

      <Tabs value={mode} onValueChange={(v) => setMode(v as "currency" | "gold")}>
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="currency">{t("converter")}</TabsTrigger>
          <TabsTrigger value="gold">{t("goldCalc")}</TabsTrigger>
        </TabsList>
      </Tabs>

      {mode === "currency" ? (
        <div className="space-y-3 rounded-2xl bg-card p-4">
          <div>
            <label className="mb-1 block text-xs font-bold text-muted-foreground">
              {t("amount")}
            </label>
            <Input
              type="number"
              inputMode="decimal"
              dir="ltr"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="h-11 text-base"
            />
          </div>
          <div className="grid grid-cols-[1fr_auto_1fr] items-end gap-2">
            <div>
              <label className="mb-1 block text-xs font-bold text-muted-foreground">{t("from")}</label>
              <AssetSelect value={fromId} onChange={setFromId} ids={currencyIds} label={t("from")} />
            </div>
            <Button
              variant="secondary"
              size="icon"
              className="h-10 w-10 shrink-0"
              onClick={swap}
              aria-label={t("swap")}
            >
              <ArrowLeftRight className="h-4 w-4" />
            </Button>
            <div>
              <label className="mb-1 block text-xs font-bold text-muted-foreground">{t("to")}</label>
              <AssetSelect value={toId} onChange={setToId} ids={currencyIds} label={t("to")} />
            </div>
          </div>

          <div className="rounded-xl bg-secondary p-4 text-center">
            <p className="text-2xl font-extrabold tnum" dir="ltr">
              {fmt(converted, converted >= 100 ? 0 : 2)}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              {fromAsset && toAsset && (
                <>
                  1 {locale === "fa" ? fromAsset.nameFa : fromAsset.nameEn} ={" "}
                  {fmt(fromRate && toRate ? fromRate / toRate : NaN, 4)}{" "}
                  {locale === "fa" ? toAsset.nameFa : toAsset.nameEn}
                </>
              )}
            </p>
            <p className="mt-1 text-[11px] text-muted-foreground">
              {offline ? t("staleWarning") : ""}
              {fromRate && t("rateSource", {
                src: byId.get(fromId)?.source.labelFa ?? "",
                time: "",
              })}
            </p>
          </div>

          {recentPairs.length > 0 && (
            <div>
              <p className="mb-1.5 text-xs font-bold text-muted-foreground">{t("recentPairs")}</p>
              <div className="flex flex-wrap gap-1.5">
                {recentPairs.map(([f, tt]) => {
                  const fa = getAsset(f);
                  const ta = getAsset(tt);
                  if (!fa || !ta) return null;
                  return (
                    <button
                      key={`${f}-${tt}`}
                      className="rounded-full bg-secondary px-3 py-1 text-xs font-semibold hover:bg-accent"
                      onClick={() => {
                        setFromId(f);
                        setToId(tt);
                      }}
                    >
                      {fa.badge}→{ta.badge}
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      ) : (
        <div className="space-y-3 rounded-2xl bg-card p-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-bold text-muted-foreground">{t("weight")}</label>
              <Input
                type="number"
                inputMode="decimal"
                dir="ltr"
                value={weight}
                onChange={(e) => setWeight(e.target.value)}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-bold text-muted-foreground">{t("purity")}</label>
              <div className="flex gap-1.5">
                {[18, 24].map((k) => (
                  <Button
                    key={k}
                    size="sm"
                    variant={karat === k ? "default" : "secondary"}
                    className="flex-1"
                    onClick={() => setKarat(k)}
                  >
                    {t(k === 18 ? "goldPurity18" : "goldPurity24")}
                  </Button>
                ))}
              </div>
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-bold text-muted-foreground">{t("unitGram")}</label>
            <Tabs value={unit} onValueChange={(v) => setUnit(v as WeightUnit)}>
              <TabsList className="grid w-full grid-cols-3">
                <TabsTrigger value="gram">{t("unitGram")}</TabsTrigger>
                <TabsTrigger value="mithqal">
                  {t("unitMithqal")} <span className="text-[10px] opacity-60">(≈{GRAMS_PER_MITHQAL.toFixed(2)})</span>
                </TabsTrigger>
                <TabsTrigger value="ounce">
                  {t("unitOunce")} <span className="text-[10px] opacity-60">(≈{GRAMS_PER_OUNCE.toFixed(1)})</span>
                </TabsTrigger>
              </TabsList>
            </Tabs>
          </div>
          <div className="grid grid-cols-3 gap-2">
            <div>
              <label className="mb-1 block text-[11px] font-bold text-muted-foreground">{t("makingCharge")}</label>
              <Input type="number" dir="ltr" value={making} onChange={(e) => setMaking(e.target.value)} className="h-9" />
            </div>
            <div>
              <label className="mb-1 block text-[11px] font-bold text-muted-foreground">{t("profitMargin")}</label>
              <Input type="number" dir="ltr" value={profit} onChange={(e) => setProfit(e.target.value)} className="h-9" />
            </div>
            <div>
              <label className="mb-1 block text-[11px] font-bold text-muted-foreground">{t("vat")}</label>
              <Input type="number" dir="ltr" value={vat} onChange={(e) => setVat(e.target.value)} className="h-9" />
            </div>
          </div>

          <div className="rounded-xl bg-secondary p-4">
            <p className="text-xs font-bold text-muted-foreground">{t("retailEstimate")}</p>
            <p className="mt-1 text-2xl font-extrabold tnum" dir="ltr">
              {fmt(gold.total, 0)}{" "}
              <span className="text-sm font-medium text-muted-foreground">
                {quoteCurrencyLabel("IRT", locale)}
              </span>
            </p>
            <dl className="mt-3 space-y-1.5 border-t border-border pt-3 text-xs">
              {[
                [t("materialPrice"), fmt(gold.materialValue, 0)],
                [t("makingCharge"), fmt(gold.makingCharge, 0)],
                [t("profitMargin"), fmt(gold.profit, 0)],
                [t("vat"), fmt(gold.vat, 0)],
              ].map(([k, v]) => (
                <div key={k} className="flex justify-between">
                  <dt className="text-muted-foreground">{k}</dt>
                  <dd className="font-bold tnum" dir="ltr">{v}</dd>
                </div>
              ))}
            </dl>
            <p className="mt-2 text-[11px] leading-4 text-muted-foreground">
              {t("retailDisclaimer")}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
