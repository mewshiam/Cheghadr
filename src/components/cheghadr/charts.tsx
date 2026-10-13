"use client";

import React, { useMemo, useRef, useState } from "react";
import type { PricePoint } from "@/lib/market/types";
import { formatAxisPrice, formatDateTime, type DigitStyle } from "@/lib/market/format";
import type { Locale } from "@/lib/i18n";

/** Tiny trend line for cards (no axes). */
export function Sparkline({
  points,
  up,
  width = 88,
  height = 30,
}: {
  points: PricePoint[];
  up: boolean;
  width?: number;
  height?: number;
}) {
  const path = useMemo(() => {
    if (points.length < 2) return null;
    const vs = points.map((p) => p.v);
    const min = Math.min(...vs);
    const max = Math.max(...vs);
    const span = max - min || max || 1;
    const dx = width / (points.length - 1);
    const d = points
      .map((p, i) => {
        const x = i * dx;
        const y = height - 3 - ((p.v - min) / span) * (height - 6);
        return `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(" ");
    return { d, area: `${d} L${width},${height} L0,${height} Z` };
  }, [points, width, height]);

  if (!path) return <div style={{ width, height }} />;
  const color = up ? "var(--up)" : "var(--down)";
  const gid = up ? "sg" : "sr";
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-hidden className="shrink-0">
      <defs>
        <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.25" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={path.area} fill={`url(#${gid})`} />
      <path d={path.d} fill="none" stroke={color} strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}

/** Interactive line chart with crosshair value inspection. */
export function LineChart({
  points,
  up,
  decimals,
  locale,
  calendar,
  digits,
  height = 240,
  labelStart,
  labelEnd,
}: {
  points: PricePoint[];
  up: boolean;
  decimals: number;
  locale: Locale;
  calendar: "jalali" | "gregorian";
  digits: DigitStyle;
  height?: number;
  labelStart?: string;
  labelEnd?: string;
}) {
  const W = 600;
  const H = height;
  const PAD_T = 12;
  const PAD_B = 22;
  const ref = useRef<SVGSVGElement>(null);
  const [hover, setHover] = useState<{ x: number; y: number; p: PricePoint } | null>(null);

  const geom = useMemo(() => {
    if (points.length < 2) return null;
    const vs = points.map((p) => p.v);
    let min = Math.min(...vs);
    let max = Math.max(...vs);
    const pad = (max - min || max || 1) * 0.08;
    min -= pad;
    max += pad;
    const t0 = points[0].t;
    const t1 = points[points.length - 1].t;
    const spanT = t1 - t0 || 1;
    const spanV = max - min || 1;
    const toXY = (p: PricePoint) => ({
      x: ((p.t - t0) / spanT) * W,
      y: PAD_T + (1 - (p.v - min) / spanV) * (H - PAD_T - PAD_B),
    });
    const d = points
      .map((p, i) => {
        const { x, y } = toXY(p);
        return `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(" ");
    return { d, toXY, min, max, t0, t1, area: `${d} L${W},${H - PAD_B} L0,${H - PAD_B} Z` };
  }, [points, H]);

  if (!geom) {
    return (
      <div className="h-40 grid place-items-center text-sm text-muted-foreground">
        —
      </div>
    );
  }

  const color = up ? "var(--up)" : "var(--down)";
  const onMove = (clientX: number) => {
    const svg = ref.current;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const rel = ((clientX - rect.left) / rect.width) * W;
    // find nearest point
    let best = points[0];
    let bestDx = Infinity;
    for (const p of points) {
      const { x } = geom.toXY(p);
      const dx = Math.abs(x - rel);
      if (dx < bestDx) {
        bestDx = dx;
        best = p;
      }
    }
    const xy = geom.toXY(best);
    setHover({ x: xy.x, y: xy.y, p: best });
  };

  return (
    <div className="relative select-none" dir="ltr">
      <svg
        ref={ref}
        viewBox={`0 0 ${W} ${H}`}
        className="w-full touch-pan-y"
        style={{ height }}
        onMouseMove={(e) => onMove(e.clientX)}
        onMouseLeave={() => setHover(null)}
        onTouchStart={(e) => onMove(e.touches[0].clientX)}
        onTouchMove={(e) => onMove(e.touches[0].clientX)}
        onTouchEnd={() => setHover(null)}
        role="img"
        aria-label="price chart"
      >
        <defs>
          <linearGradient id="cg" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.22" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        {/* min/max gridlines */}
        {[0.25, 0.5, 0.75].map((f) => {
          const y = PAD_T + f * (H - PAD_T - PAD_B);
          return <line key={f} x1="0" y1={y} x2={W} y2={y} stroke="var(--border)" strokeWidth="1" strokeDasharray="3 5" />;
        })}
        <path d={geom.area} fill="url(#cg)" />
        <path d={geom.d} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
        {hover && (
          <g>
            <line className="crosshair-line" x1={hover.x} y1={PAD_T} x2={hover.x} y2={H - PAD_B} stroke="var(--muted-foreground)" strokeWidth="1" />
            <circle cx={hover.x} cy={hover.y} r="4" fill={color} stroke="var(--card)" strokeWidth="2" />
          </g>
        )}
        {/* min & max labels */}
        <text x="4" y={PAD_T + 8} fontSize="10" fill="var(--muted-foreground)">
          {formatAxisPrice(geom.max, decimals, digits)}
        </text>
        <text x="4" y={H - PAD_B - 4} fontSize="10" fill="var(--muted-foreground)">
          {formatAxisPrice(geom.min, decimals, digits)}
        </text>
      </svg>
      <div className="flex justify-between px-1 text-[11px] text-muted-foreground" dir={locale === "fa" ? "rtl" : "ltr"}>
        <span>{labelStart ?? formatDateTime(points[0]?.t ?? 0, locale, calendar, digits)}</span>
        <span>{labelEnd ?? formatDateTime(points[points.length - 1]?.t ?? 0, locale, calendar, digits)}</span>
      </div>
      {hover && (
        <div
          className="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full rounded-lg border bg-popover px-2.5 py-1.5 text-xs shadow-md tnum"
          style={{
            left: `${(hover.x / W) * 100}%`,
            top: `${(hover.y / H) * height}px`,
          }}
          dir={locale === "fa" ? "rtl" : "ltr"}
        >
          <div className="font-semibold" style={{ color }}>
            {formatAxisPrice(hover.p.v, decimals, digits)}
          </div>
          <div className="text-muted-foreground">
            {formatDateTime(hover.p.t, locale, calendar, digits)}
          </div>
        </div>
      )}
    </div>
  );
}
