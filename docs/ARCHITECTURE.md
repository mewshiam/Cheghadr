# Architecture

## Modules

The project follows the modular layout suggested in the product blueprint,
adjusted to implementation scale (search lives inside `feature:markets`,
favorites inside `feature:watchlist`):

```
:app                       entry point: manifest, MainActivity, navigation,
                           Hilt application, WorkManager workers, boot receiver
├── core:model             pure-Kotlin domain models (no Android deps)
├── core:common            formatting (Persian/Latin digits, money), Jalali
│                          calendar, freshness classification, theme enums
├── core:designsystem      Material 3 theme + Vazirmatn typography + shared
│                          composables (single source of UI truth)
├── core:data              Room DB, DataStore settings, Ktor networking,
│                          providers, repositories, sync coordinator, catalog
├── core:notifications     alert notification channel + rendering
├── core:widgets           Glance app widgets (single price + watchlist list),
│                          widget config activity, widget updater
├── feature:home           dashboard: featured prices, watchlist preview, status
├── feature:markets        category explorer + universal search
├── feature:watchlist      favorites management (reorder, remove)
├── feature:assetdetails   quote detail, custom-Canvas line chart, share
├── feature:converter      currency converter + gold calculator
├── feature:alerts         alert rules CRUD + notification preferences
├── feature:portfolio      local-only transactions and P/L
└── feature:settings       language, appearance, data, sources config, privacy
```

Dependencies only point downwards (`feature → core`, `core:x → core:model`),
never sideways; `:app` is the only module that knows about all of them.

## Data flow (unidirectional)

```
 Provider HTTP (Ktor)            Room (quotes/history/status)        Compose UI
 ┌──────────────────┐   raw      ┌───────────────────────────┐  Flow  ┌──────────┐
 │ CoinPaprika      │──RawQuote─▶│ SyncCoordinator           │───────▶│ ViewModel│
 │ open.er-api.com  │            │  • validate (Normalizer)  │        │  State   │
 │ Frankfurter      │            │  • never overwrite valid  │        │   ↓      │
 │ gold-api.com     │            │    cache with bad data    │        │  Screen  │
 │ CoinGecko (opt.) │            │  • backoff + jitter       │        │   ↑      │
 │ tgju.org scraper │            │  • sample local history   │        │  events  │
 └──────────────────┘            └───────────────────────────┘        └──────────┘
        ▲                                    ▲                            │
        │                                    │                            ▼
        │                            DataStore (settings)         repositories
        └────────────── WorkManager periodic sync (≥15 min, connectivity-
                        constrained; interval is a request, not a promise)
```

Key invariants:

- **Canonical IDs** (`crypto:BTC`, `fx:USD`, `gold:GRAM18`, …) are the only
  cross-layer identifiers; display names are never used as keys.
- **Money is `BigDecimal` end-to-end** (stored as canonical strings in Room);
  binary floating point never touches monetary values.
- **Every quote carries** provider id, fetch time, provider-side price time
  when exposed, and provenance — the UI surfaces them.
- **Nothing overwrites valid cached data with malformed responses**: the
  normalizer rejects non-positive/absurd prices, implausible changes, future
  timestamps; failures only update provider health.

## Refresh policy

| Trigger | Behaviour |
|---|---|
| Cold start | cached render + one quiet background refresh (backoff-respecting) |
| Foreground return | widgets repainted from cache |
| Pull-to-refresh | manual refresh bypasses backoff |
| Periodic WorkManager | default 15–240 min (user setting), network-constrained, exponential backoff with jitter after failures |
| Widget tap | opens detail; refresh button re-renders from cache |

Android may defer any background work; the UI states the **last successful
update** instead of promising real-time delivery.

## Localization & calendar

`values/` is Persian (the app's default language), `values-en/` English; the
locale is applied via per-app locales (`AppCompatDelegate`) so switching in
Settings needs no reinstall. The Jalali conversion is the canonical
Pournader–Toossi algorithm (unit-tested); timestamps are stored as UTC epoch
millis and formatted per locale at render time. Mixed Persian/Latin text uses
bidi isolation so tickers and numbers never reverse.

## Widgets

Two Glance widgets (`SINGLE_ASSET`, `ASSET_LIST`) are declared by
`:core:widgets` and merged into the app manifest. Configuration is persisted
per `appWidgetId` in the `widget_configs` table (separate from UI state);
widgets read **only from the Room cache**, repaint after every successful sync,
are re-rendered on boot by `BootReceiver`, and label stale values.
