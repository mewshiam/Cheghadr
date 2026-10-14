# Features — implemented vs. deferred

## Implemented (v1.0.0)

### Core market experience
- **Home dashboard** — app identity header, market status + last successful
  refresh, featured prices (falls back to a sensible default set), daily
  absolute/percentage changes, watchlist preview, quick converter access.
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
| CSV / image export | deferred (text share works) | share-with-source implemented; CSV export is next in line |
| Settings/watchlist backup import-export | deferred | needs a stable format decision |
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
