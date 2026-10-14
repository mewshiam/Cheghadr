# Data sources — verified capability matrix

All endpoints below were **verified live from the build environment on
2026-10-14**; sample responses are embedded in the parser unit tests
(`core/data/src/test/.../ProviderParsingTest.kt`, `TgjuScraperTest.kt`).
Nothing in this document is assumed from documentation alone.

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
  rates. Free-market rates come from the tgju.org scraper below.
- **Frankfurter cross pairs** quote foreign units per USD; the converter
  inverts them and labels the provenance.
- **gold-api.com has no history** — the app accumulates device-side
  observations (≤1/hour, 2-year retention) and charts those, clearly marked.

## Scraped provider (on by default, user-toggleable)

### tgju.org — Iranian market data

- Investigated live on 2026-10-14: `https://www.tgju.org/` and its section
  and profile pages (structure verified with captured fixtures).
- **Finding:** the documented web-service API (`api.tgju.org`) is a paid
  product and was unreachable (HTTP 404 / intermittent). The site's **public
  HTML pages**, however, are server-rendered and reachable — the app scrapes
  those directly, at the repo owner's explicit request.
- **Implementation** (`TgjuProvider` + `TgjuScraper`, Jsoup):
  - Quotes — three section pages per refresh, fetched in parallel:
    `/currency` (free-market FX), `/gold-chart` (gold), `/coin` (coins).
    Rows are identified by `tr[data-market-row]` + `data-price`; the change
    cell's `span.high|low` class carries the change sign; daily low/high and
    the update time come from sibling cells.
  - Sidebar — every page embeds a site-wide live box (`li#l-{symbol}`) which
    is the only server-rendered source for `gc30` (TEDPIX) and `oil_brent`.
  - History — `/profile/{symbol}` renders a ~25-day daily OHLC table
    (Jalali dates, close column) plus the live sidebar price for today;
    `oil_brent` has no profile page (redirects) and stays spot-only.
  - Robustness — every fetch retries transient Cloudflare 404s (observed:
    a valid profile URL answered 404 once, then 200); browser-like headers
    are sent; numbers may be ASCII/Persian digits with `,`/`٬` separators.
- **Catalog symbols verified against the live site:** `price_dollar_rl`,
  `price_eur`, `price_aed`, `price_try`, `geram18`, `geram24`, `mesghal`,
  `sekee` (Emami), `sekeb` (Bahar Azadi), `nim`, `rob`, `gerami`, `gc30`
  (TEDPIX), `oil_brent`.
- **Costs & politeness:** three HTML pages ≈ 1.5 MB per refresh (no gzip —
  kept off the shared client to avoid regressing the JSON providers);
  requests happen only at the sync cadence (default 30 min) with exponential
  backoff on failure. Disable anytime in Settings → منابع داده.
- **Compliance:** scraping happens on-device for personal display; the app
  sends no credentials, bypasses nothing (no CAPTCHA evasion, no identity
  rotation, no rate-limit evasion) and shows TGJU attribution on Iranian
  markets. The repo owner accepts responsibility for complying with the
  site's terms.

## Rejected / not integrated

| Source | Reason |
|---|---|
| CoinGecko public API | verified working but repeatedly HTTP 429 within a short window; kept as optional secondary, not the default |
| CoinMarketCap | free tier requires an API key at build time; key handling policy prefers user-supplied keys → not integrated for v1 |
| Nobitex / domestic exchanges | DNS-blocked from the build environment; could not verify terms or responses — not integrated |
| tgju.org crypto table | USD-quoted global prices duplicate CoinPaprika coverage with no history → crypto stays on CoinPaprika |

## Per-category coverage after all of the above

| Category | Live source | Notes |
|---|---|---|
| CRYPTO | CoinPaprika | quotes + real history |
| CURRENCY (official) | open.er-api + Frankfurter | IRR-quoted officials + USD crosses |
| CURRENCY (free market) | tgju.org scraper | USD, EUR, AED, TRY — rial-quoted, ~25-day history |
| GOLD (global ounce) | gold-api.com | spot, USD |
| GOLD (gram/mithqal, Iranian) | tgju.org scraper | geram18/24 + mesghal, ~25-day history |
| COIN | tgju.org scraper | Emami, Bahar Azadi, half, quarter, gerami |
| STOCK (Tehran indices) | tgju.org scraper | TEDPIX via site sidebar + profile history |
| COMMODITY (silver) | gold-api.com | spot, USD |
| COMMODITY (Brent oil) | tgju.org scraper | spot via sidebar, USD; no profile history (local observations) |
