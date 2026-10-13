import type { Quote } from "./types";
import type { AlertRule } from "./store";

/**
 * Local alert evaluation (blueprint §11): threshold crossings with dedupe,
 * re-arm on crossing back, and stale-data suppression.
 */

export interface AlertEvaluation {
  rule: AlertRule;
  fired: boolean;
  /** re-arm state after evaluation */
  armed: boolean;
}

export function evaluateAlert(
  rule: AlertRule,
  quote: Quote | undefined,
  opts: { allowStale?: boolean } = {},
): AlertEvaluation {
  if (!rule.enabled || !quote) {
    return { rule, fired: false, armed: rule.armed };
  }
  // Never trigger from stale data unless explicitly opted in (blueprint §11).
  if (quote.freshness === "stale" && !opts.allowStale) {
    return { rule, fired: false, armed: rule.armed };
  }

  const { type, value } = rule;
  const price = quote.price;
  const pct = quote.changePercent;
  let fired = false;
  let armed = rule.armed;

  switch (type) {
    case "above": {
      if (armed && price >= value) {
        fired = true;
        armed = false;
      } else if (!armed && price < value) {
        armed = true; // crossed back below → re-arm
      }
      break;
    }
    case "below": {
      if (armed && price <= value) {
        fired = true;
        armed = false;
      } else if (!armed && price > value) {
        armed = true;
      }
      break;
    }
    case "pctUp": {
      if (armed && pct >= value) {
        fired = true;
        armed = false;
      } else if (!armed && pct < value) {
        armed = true;
      }
      break;
    }
    case "pctDown": {
      if (armed && pct <= -value) {
        fired = true;
        armed = false;
      } else if (!armed && pct > -value) {
        armed = true;
      }
      break;
    }
  }
  return { rule, fired, armed };
}

/** Evaluate all enabled alerts for the current quote snapshot. */
export function evaluateAlerts(
  rules: AlertRule[],
  quotes: Quote[],
  opts: { allowStale?: boolean } = {},
): { fired: { rule: AlertRule; quote: Quote }[]; updates: { id: string; armed: boolean; lastTriggeredAt?: number }[] } {
  const byId = new Map(quotes.map((q) => [q.assetId, q]));
  const fired: { rule: AlertRule; quote: Quote }[] = [];
  const updates: { id: string; armed: boolean; lastTriggeredAt?: number }[] = [];
  const now = Date.now();
  for (const rule of rules) {
    const res = evaluateAlert(rule, byId.get(rule.assetId), opts);
    if (res.armed !== rule.armed || res.fired) {
      updates.push({
        id: rule.id,
        armed: res.armed,
        lastTriggeredAt: res.fired ? now : rule.lastTriggeredAt,
      });
    }
    if (res.fired) {
      const quote = byId.get(rule.assetId);
      if (quote) fired.push({ rule, quote });
    }
  }
  return { fired, updates };
}
