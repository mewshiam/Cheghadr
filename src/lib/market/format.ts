import * as jalaali from "jalaali-js";
import type { Locale } from "@/lib/i18n";

/** Persian digit shaping (blueprint §16: Persian digits by default, Latin optional). */
const FA_DIGITS = ["۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"];

export type DigitStyle = "persian" | "latin";

export function toDigits(s: string, style: DigitStyle): string {
  if (style === "latin") return s;
  return s.replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]);
}

export interface FormatNumOpts {
  decimals?: number;
  digits?: DigitStyle;
  locale: Locale;
  /** always show sign, e.g. +12 / -3 */
  signed?: boolean;
  /** compact large numbers: 1.2B / 1.2 میلیارد */
  compact?: boolean;
}

/** Localized, decimal-safe display formatting. Financial math stays in numbers with care. */
export function formatNumber(value: number, opts: FormatNumOpts): string {
  const { decimals = 0, digits = "persian", locale, signed = false, compact = false } = opts;
  if (!Number.isFinite(value)) return "—";
  const fa = locale === "fa";
  const abs = Math.abs(value);
  let body: string;
  if (compact && abs >= 1_000_000_000) {
    body = (value / 1_000_000_000).toFixed(1) + (fa ? " میلیارد" : "B");
  } else if (compact && abs >= 1_000_000) {
    body = (value / 1_000_000).toFixed(1) + (fa ? " میلیون" : "M");
  } else {
    body = value.toLocaleString("en-US", {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    });
  }
  if (signed && value > 0) body = "+" + body;
  return toDigits(body, digits);
}

/** Quote currency labels per locale. */
export function quoteCurrencyLabel(ccy: string, locale: Locale): string {
  const map: Record<string, [string, string]> = {
    IRT: ["تومان", "Toman"],
    IRR: ["ریال", "Rial"],
    USD: ["دلار", "USD"],
    "": ["امتیاز", "points"],
  };
  const pair = map[ccy];
  if (!pair) return ccy;
  return locale === "fa" ? pair[0] : pair[1];
}

/** ISO-ish UTC→Jalali/Gregorian date string. */
export function formatDate(
  ts: number,
  locale: Locale,
  calendar: "jalali" | "gregorian",
  digits: DigitStyle,
): string {
  const d = new Date(ts);
  if (calendar === "jalali") {
    const j = jalaali.toJalaali(d.getFullYear(), d.getMonth() + 1, d.getDate());
    const months = [
      "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
      "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    ];
    const s = `${j.jd} ${months[j.jm - 1]} ${j.jy}`;
    return toDigits(s, digits);
  }
  const months = [
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
  ];
  const s = `${months[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
  return toDigits(s, digits);
}

export function formatTime(ts: number, digits: DigitStyle): string {
  const d = new Date(ts);
  const pad = (n: number) => String(n).padStart(2, "0");
  const s = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return toDigits(s, digits);
}

export function formatDateTime(
  ts: number,
  locale: Locale,
  calendar: "jalali" | "gregorian",
  digits: DigitStyle,
): string {
  return `${formatDate(ts, locale, calendar, digits)} • ${formatTime(ts, digits)}`;
}

/** Relative time in the active locale. */
export function formatRelative(
  ts: number,
  now: number,
  locale: Locale,
  digits: DigitStyle,
): string {
  const diff = Math.max(0, Math.floor((now - ts) / 1000));
  const fa = locale === "fa";
  if (diff < 60) return fa ? "همین حالا" : "just now";
  const mins = Math.floor(diff / 60);
  if (mins < 60) {
    return toDigits(
      fa ? `${mins} دقیقه پیش` : `${mins}m ago`,
      digits,
    );
  }
  const hours = Math.floor(mins / 60);
  if (hours < 24) {
    return toDigits(fa ? `${hours} ساعت پیش` : `${hours}h ago`, digits);
  }
  return formatDate(ts, locale, "gregorian", digits);
}

/** Chart axis value formatting (short form). */
export function formatAxisPrice(v: number, decimals: number, digits: DigitStyle): string {
  const abs = Math.abs(v);
  if (abs >= 1_000_000) return toDigits((v / 1_000_000).toFixed(1) + "M", digits);
  if (abs >= 10_000) return toDigits((v / 1000).toFixed(1) + "k", digits);
  return toDigits(v.toFixed(decimals), digits);
}
