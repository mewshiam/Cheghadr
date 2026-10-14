# «چقدر؟» — Cheghadr

**A native Android market tracker for Iranian and international markets.**

Cheghadr («چقدر؟» — *“How much?”*) tracks currencies, gold, coins, cryptocurrencies
and more with one non-negotiable rule: **every price discloses its source and its
age.** No invented numbers, no silent scraping, no fake fallbacks.

- **Native** Kotlin + Jetpack Compose (Material 3) — not a WebView, not a wrapper.
- **Persian-first**: RTL layout, Vazirmatn typography, Jalali dates, Persian
  digits by default — with complete English localization.
- **Honest data**: live, keyless sources (CoinPaprika, open.er-api, Frankfurter,
  gold-api.com) plus an opt-in, user-configured TGJU-compatible source for the
  Iranian free market.
- **Useful offline**: Room-cached quotes render instantly and are marked stale;
  favorites, alerts and settings persist locally.
- **Android-native extras**: home-screen widgets (Glance), price alerts with
  quiet hours, WorkManager-backed sync with exponential backoff.

```
…خانه ── بازارها ── دیده‌بان ── مبدل ── بیشتر…
Home     Markets     Watchlist   Converter   More
```

## Quick start

Requirements: **JDK 17**, **Android SDK** (platform 35, build-tools 34), git.

```bash
git clone https://github.com/mewshiam/Cheghadr.git
cd Cheghadr
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # unit tests (exact count: docs/TESTING.md)
./gradlew assembleRelease        # unsigned release APK (R8-minified)
```

Install the debug APK on a device: `adb install app/build/outputs/apk/debug/app-debug.apk`
—or just download it from the [Releases](../../releases) page.

For signing keys, CI, and toolchain details see [docs/BUILD.md](docs/BUILD.md).
For what works without any configuration see [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md).

## What you get on first launch

| Market | Source (no key needed) | Live |
|---|---|---|
| Cryptocurrencies (16) | CoinPaprika `/v1/tickers` + daily history | ✅ |
| Global gold & silver (XAU/XAG) | gold-api.com spot | ✅ |
| Official FX in rial (USD, EUR, GBP, AED, …) | open.er-api.com | ✅ |
| International crosses (EUR/USD, GBP/USD, …) | Frankfurter (ECB) + real history | ✅ |
| Iranian free market (دلار آزاد، طلا، سکه) | your authorized TGJU-compatible endpoint | ⚙️ opt-in |
| Tehran stock indices | requires an authorized source | ❌ honest “unavailable” state |

## Documentation

| Doc | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | modules, data flow, refresh policy |
| [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md) | verified source matrix, endpoints, quotas |
| [docs/DATA_LICENSES.md](docs/DATA_LICENSES.md) | licensing & redistribution considerations |
| [docs/BUILD.md](docs/BUILD.md) | toolchain, signing, release checklist |
| [docs/FEATURES.md](docs/FEATURES.md) | implemented vs. deferred features |
| [docs/TESTING.md](docs/TESTING.md) | executed tests & results |
| [docs/PRIVACY.md](docs/PRIVACY.md) | what the app stores and sends where |

`LICENSE-OFL.txt` covers the bundled **Vazirmatn** font (SIL Open Font License 1.1).
The application code itself currently carries no license — the repository owner
should add one (see docs/BUILD.md → Release checklist).

## Credits

Market data by [CoinPaprika](https://coinpaprika.com/),
[ExchangeRate-API (open endpoint)](https://www.exchangerate-api.com),
[Frankfurter / ECB](https://frankfurter.dev) and
[gold-api.com](https://gold-api.com) — terms in docs/DATA_SOURCES.md.
Font: [Vazirmatn](https://github.com/rastikerdar/vazirmatn) (OFL-1.1).

---

*Cheghadr should feel like a polished Android finance utility: fast to open,
easy to understand, minimal by default, customizable for power users, useful
offline — and honest about where every price comes from.*
