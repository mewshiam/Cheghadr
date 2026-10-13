/**
 * «چقدر؟» deterministic calculation library (blueprint §8).
 * Pure functions only — unit-testable, no side effects, no hidden state.
 */

/** Mithqal = 4.6083 g (official Iranian convention). */
export const GRAMS_PER_MITHQAL = 4.6083;
/** Troy ounce in grams. */
export const GRAMS_PER_OUNCE = 31.1034768;

export type WeightUnit = "gram" | "mithqal" | "ounce";

export function toGrams(weight: number, unit: WeightUnit): number {
  switch (unit) {
    case "mithqal":
      return weight * GRAMS_PER_MITHQAL;
    case "ounce":
      return weight * GRAMS_PER_OUNCE;
    default:
      return weight;
  }
}

/**
 * Currency conversion through a common IRT base.
 * rateFrom/rateTo are toman prices of 1 unit of each currency.
 */
export function convertCurrency(
  amount: number,
  rateFrom: number,
  rateTo: number,
): number {
  if (!Number.isFinite(amount) || !Number.isFinite(rateFrom) || !Number.isFinite(rateTo)) {
    return NaN;
  }
  if (rateFrom <= 0 || rateTo <= 0) return NaN;
  return (amount * rateFrom) / rateTo;
}

export interface GoldCalcInput {
  weight: number;
  unit: WeightUnit;
  /** karat: 14..24 */
  karat: number;
  /** market price of 1 gram of 24k gold, in toman */
  gram24Price: number;
  /** making charge, percent of raw value */
  makingChargePct: number;
  /** seller profit margin, percent of raw value */
  profitPct: number;
  /**
   * VAT percent — applied ONLY to (making charge + profit), matching the
   * common Iranian retail convention where raw gold is VAT-exempt.
   */
  vatPct: number;
}

export interface GoldCalcResult {
  pureGrams: number;
  materialValue: number;
  makingCharge: number;
  profit: number;
  vat: number;
  total: number;
}

/** Retail gold estimate with explicit breakdown (labeled as an estimate in UI). */
export function goldRetailEstimate(input: GoldCalcInput): GoldCalcResult {
  const {
    weight,
    unit,
    karat,
    gram24Price,
    makingChargePct,
    profitPct,
    vatPct,
  } = input;
  const grams = toGrams(weight, unit);
  const purity = Math.min(Math.max(karat, 1), 24) / 24;
  const pureGrams = grams * purity;
  const materialValue = pureGrams * gram24Price;
  const makingCharge = materialValue * (makingChargePct / 100);
  const profit = materialValue * (profitPct / 100);
  const vat = (makingCharge + profit) * (vatPct / 100);
  const total = materialValue + makingCharge + profit + vat;
  return {
    pureGrams: Math.round(pureGrams * 100) / 100,
    materialValue: Math.round(materialValue),
    makingCharge: Math.round(makingCharge),
    profit: Math.round(profit),
    vat: Math.round(vat),
    total: Math.round(total),
  };
}

/** Convert a coin price into its equivalent gold-gram content value. */
export function coinGramEquivalent(coinPrice: number, gram24Price: number): number {
  if (gram24Price <= 0) return NaN;
  return Math.round((coinPrice / gram24Price) * 100) / 100;
}
