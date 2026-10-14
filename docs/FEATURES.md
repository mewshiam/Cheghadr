# Features — implemented vs. deferred

## Implemented (v1.1.0)

### Core market experience
- **Home dashboard (customizable, blueprint §7)** — app identity header, market
  status + last successful refresh, featured prices, daily absolute/percentage
  changes, watchlist preview, quick converter access. The layout is fully
  user-configurable: sections can be shown/hidden and reordered, featured
  assets can be pinned manually (searchable picker, max 8) or left automatic
  (watchlist-first), density is compact/comfortable, mini sparkline charts
  (from this device's recorded observations) and change badges can be toggled,
  and the whole layout can be reset to defaults. Everything persists locally.
- **Optional Top-movers overview** — biggest 24h gainers/losers among available
  assets, hidden by default, enable it from dashboard customization.
- **Markets explorer** — category tabs (ارز، طلا، سکه، رمزارز، بورس، کالا),
  pull-to-refresh, loading/empty/error/unavailable states per category,
  honest "needs an authorized source" cards for TGJU-gated markets.
- **Universal search** — Persian + English names, tickers and aliases, with
  Persian text normalization (ی/ي، ک/ك، ZWNJ) and search history (clearable).
- **Asset details** — quote with source + freshness, absolute/percentage
  change, custom Canvas line chart with range selection (1D…MAX where
  supported), favorite toggle, create-alert action, share action with source
  and timestamp disclosure, per-asset history table (local observations).
- **Watchlist** — favorites persist across restarts; reorder and remove.

### Calculators (deterministic, unit-tested)
- **Currency converter** — amount input, source/destination selection, swap,
  popular quick-picks, last-used pairs, exact `BigDecimal` math bridged
  through USD, stale-rate warning with source disclosure, offline conversion
  from cached rates.
- **Gold calculator** — gram/mithqal/ounce conversions, 18k–24k purity
  conversions, coin intrinsic values from published specs, optional making
  charge/tax/discount inputs with an explicit "estimate" label and visible
  breakdown (market price kept separate from retail estimate).

- **Search, favorites & sharing** — universal search with history, favorites,
  native share sheet with source disclosure, plus **CSV export** of portfolio
  transactions and of the displayed price history (with a `source` column),
  and a **local backup export** (settings + watchlist + dashboard layout as
  JSON — API keys and custom endpoint URLs are never included).

### Android integration
- **Widgets** (Glance): single-price widget + multi-asset watchlist widget;
  per-widget configuration persisted in Room; repaint after syncs and on
  boot; tap → asset detail; stale values labeled; no per-widget networking.
- **Price alerts** — above/below/percent-up/percent-down thresholds,
  recurring vs. one-shot, cooldown, re-arming after crossing back, duplicate
  prevention (pure `AlertEvaluator`, tested), notification permission flow,
  quiet hours, notifications deep-link to the asset.
- **Background sync** — WorkManager periodic work (user interval 15–240 min),
  connectivity-constrained, exponential backoff with jitter, provider health
  recorded and surfaced in Settings; foreground/pull-to-refresh paths.
- **Offline** — Room cache renders instantly; stale classification
  (FRESH/STALE/VERY_STALE) on every quote; favorites/alerts/settings survive
  airplane mode.

### Localization & design
- Persian default (RTL, Vazirmatn 4 weights, Persian digits, Jalali dates
  via the Pournader–Toossi algorithm, correct bidi isolation) + full English
  (LTR, Gregorian, Latin digits); in-app language switch without reinstall.
- Material 3 light/dark/system + dynamic color (Android 12+); restrained
  green/red changes always paired with arrows/signs (color is never the only
  indicator); skeleton loading, empty/error/offline states throughout.

### Data integrity
- Canonical asset IDs; provider-symbol mappings; validation before persistence
  (positive prices, bounded magnitude, ±99% change sanity, future timestamps
  rejected); malformed responses never overwrite valid cache; every quote
  carries provider + timestamps + provenance.

## Deferred (documented, not promised)

| Feature | Status | Why |
|---|---|---|
| Mini-chart & converter widgets | deferred | two solid widget types first; chart-in-widget adds size/complexity |
| Multiple named watchlists | deferred | single watchlist covers the core use case |
| Backup *import* (restore) | deferred | export implemented in v1.1.0; import needs format-versioning UX |
| Price-card **image** export | deferred (CSV + text share work) | needs canvas capture pipeline |
| Provider comparison view | partial | provider health visible in Settings; side-by-side quote comparison not built |
| OHLC/candlestick charts | deferred | no integrated source currently exposes genuine OHLC data |
| Backend ingestion for TGJU | not built | requires authorization per TGJU terms; device-side opt-in endpoint instead |
| Instrumented/UI tests on emulator | not run in this environment | no emulator available here; unit + build verification executed (see TESTING.md) |

## Known limitations

- Background intervals are requests, not guarantees — Android may defer
  WorkManager execution (stated in the UI, not hidden).
- CoinGecko is registered but serves no catalog assets in v1 (rate limits
  observed during verification); crypto defaults to CoinPaprika.
- Brent oil and Tehran indices have no permissible keyless source and render
  an honest unavailable state.
- The gold calculator's retail estimate excludes market premium/bubble by
  design and says so in the UI.
