# Data sources — verified capability matrix

All endpoints below were **verified live from the build environment on
2026-10-14**; sample responses are embedded in the parser unit tests
(`core/data/src/test/.../ProviderParsingTest.kt`). Nothing in this document is
assumed from documentation alone.

## Active providers (no key, enabled by default)

| | CoinPaprika | open.er-api.com | Frankfurter | gold-api.com |
|---|---|---|---|---|
| **Role** | crypto quotes + history | official FX incl. IRR | ECB crosses + history | gold/silver spot |
| **Base URL** | `https://api.coinpaprika.com/v1` | `https://open.er-api.com/v6` | `https://api.frankfurter.dev/v1` | `https://api.gold-api.com` |
| **Endpoints used** | `/tickers?quotes=USD` (bulk), `/tickers/{id}/historical?start=…&interval=1d\|1h` | `/latest/USD` | `/latest?from=USD&to=…`, `/{start}..{end}?from=USD&to=…` | `/price/XAU`, `/price/XAG` |
| **Categories** | CRYPTO | CURRENCY (official, IRR-quoted) | CURRENCY (crosses) | GOLD, COMMODITY |
| **Update frequency** | ~minutes (tickers), daily (history) | ~daily | ECB business days | ~minutes |
| **History** | ✅ daily/hourly, years back | ❌ (local observations) | ✅ daily | ❌ (local observations) |
| **Auth** | none | none | none | none |
| **Quotas** | free personal tier, generous; bulk call used **once per sync** | free open endpoint | free, no stated hard limit | free, no key |
| **Attribution** | appreciated | exchangerate-api.com | “ECB reference rates via Frankfurter” | gold-api.com |

Observed 2026-10-14 (abridged, full fixtures in tests):

```jsonc
// api.coinpaprika.com/v1/tickers?quotes=USD
[{"id":"btc-bitcoin","symbol":"BTC","rank":1,"last_updated":"2026-10-14T06:35:38Z",
  "quotes":{"USD":{"price":83863.0,"volume_24h":1041407319399.58,
  "market_cap":1685455271599.43,"percent_change_24h":0.81}}}]

// open.er-api.com/v6/latest/USD
{"result":"success","base_code":"USD","time_last_update_unix":1760409602,
 "rates":{"USD":1,"IRR":420000,"EUR":0.92,…}}

// api.frankfurter.dev/v1/latest?from=USD&to=EUR
{"amount":1.0,"base":"USD","date":"2026-10-13","rates":{"EUR":0.92}}

// api.gold-api.com/price/XAU
{"name":"Gold","price":2662.023,"symbol":"XAU","updatedAt":"2026-10-14T06:00:00Z"}
```

### Design decisions

- **Crypto defaults to CoinPaprika** (one bulk request per sync covers the
  whole catalog) rather than CoinGecko, whose public endpoint answered HTTP
  429 during verification. CoinGecko remains registered (provider isolation)
  with an optional demo-key setting, but currently maps no catalog assets.
- **Official ≠ free market.** open.er-api publishes *official* (bank-reference)
  IRR rates; they are labeled «نرخ رسمی» and never presented as free-market
  rates. Free-market rates require the opt-in TGJU source.
- **Frankfurter cross pairs** quote foreign units per USD; the converter
  inverts them and labels the provenance.
- **gold-api.com has no history** — the app accumulates device-side
  observations (≤1/hour, 2-year retention) and charts those, clearly marked.

## Conditional provider (shipped disabled)

### TGJU-compatible source

- Investigated: `https://www.tgju.org/`, `https://help.tgju.org/`,
  `https://gem.tgju.org/terms`.
- **Finding:** the documented web-service API is a paid product, and TGJU's
  terms state that copying/redistributing content requires written permission.
  The public API endpoint was **not reachable** from the build environment
  (HTTP 404) — no undocumented or guessed endpoint was put into production
  code, per the product rules.
- **Implementation:** `TgjuProvider` parses the standard TGJU web-service JSON
  widget schema (`{"current":{"price_dollar_rl":{"p":"58,000","d":"…","dp":"…"}}}`)
  against a **user-supplied base URL** (Settings → داده‌ها). It stays disabled
  until the user enables it; enabling it flips the TGJU-backed catalog entries
  (دلار آزاد، طلای ۱۸/۲۴ عیار، مثقال، سکه‌ها، شاخص بورس) to available.
- **The user is responsible for having authorization** for the endpoint they
  configure; the app sends no credentials and bypasses nothing (no CAPTCHA
  evasion, no identity rotation, no rate-limit evasion).

## Rejected / not integrated

| Source | Reason |
|---|---|
| CoinGecko public API | verified working but repeatedly HTTP 429 within a short window; kept as optional secondary, not the default |
| CoinMarketCap | free tier requires an API key at build time; key handling policy prefers user-supplied keys → not integrated for v1 |
| Nobitex / domestic exchanges | DNS-blocked from the build environment; could not verify terms or responses — not integrated |
| TGJU scraping (HTML) | not implemented: written permission is required for redistribution; a backend ingestion worker would be needed first |

## Per-category coverage after all of the above

| Category | Live source | Notes |
|---|---|---|
| CRYPTO | CoinPaprika | quotes + real history |
| CURRENCY (official) | open.er-api + Frankfurter | IRR-quoted officials + USD crosses |
| CURRENCY (free market) | TGJU (opt-in) | honest unavailable state until configured |
| GOLD (global ounce) | gold-api.com | spot, USD |
| GOLD (gram/mithqal, Iranian) | TGJU (opt-in) | unavailable state until configured |
| COIN | TGJU (opt-in) | unavailable state until configured |
| STOCK (Tehran indices) | TGJU (opt-in) | unavailable state until configured |
| COMMODITY (silver) | gold-api.com | spot, USD |
| COMMODITY (Brent oil) | none permissible found | honest unavailable state, documented |
