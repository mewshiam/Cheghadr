# Testing

## What was actually executed (2026-10-14, this environment)

```
./gradlew test            # BUILD SUCCESSFUL
./gradlew assembleDebug   # BUILD SUCCESSFUL → app-debug.apk
./gradlew assembleRelease # BUILD SUCCESSFUL → app-release-unsigned.apk (R8)
```

**108 unit tests, 0 failures, 0 errors, 0 skipped** across 13 test classes
(`*:test` / `*:testDebugUnitTest` / `*:testReleaseUnitTest` reports under
`*/build/test-results/`).

| Module | Test classes | Coverage |
|---|---|---|
| core:model | AlertEvaluatorTest, PortfolioCalculatorTest | threshold crossings, cooldown suppression, re-arming, average cost, realized/unrealized P/L, partial sells |
| core:common | JalaliCalendarTest, MoneyFormatterTest, PriceDisplayTest, FreshnessClassifierTest, DashboardConfigTest, CsvTest | calendar round-trips vs. known dates, digit localization/separators, toman conversion, price/change rendering, freshness thresholds, dashboard-config round-trip/repair semantics (garbage decode, dedupe, section restoration, featured cap), RFC 4180 CSV escaping incl. Persian text |
| core:data | ProviderParsingTest, QuoteNormalizerTest | live-observed fixtures for every provider parser; validation rules (bad prices/changes/timestamps rejected) |
| feature:converter | CurrencyConverterTest, GoldCalculatorTest | exact BigDecimal conversion chains (IRR cross-rates, crypto, inverse pairs), mithqal/ounce weights, purity conversion, coin specs |
| app | QuietHoursTest | quiet-hours window logic incl. overnight windows |

Bugs actually caught and fixed by these tests during the build restoration:
the converter's crypto branch keyed on the wrong `PriceUnit`, a mithqal test
expectation had bad arithmetic, and two fixtures didn't match their comments —
all resolved with exact decimal semantics.

## Not executed here, and why

- **Instrumented tests / connectedAndroidTest** — no emulator or device is
  available in this environment. The debug APK was built and installs
  (architecture verified at build time), but on-device flows are unverified
  until the release checklist in docs/BUILD.md is walked through.
- **Lint** — configured and CI-wired (`./gradlew lint`, non-blocking); not
  part of the local green run above.
- **Widget/notification tests on a launcher** — requires a device; the config
  persistence layer *is* covered by catalog/model tests.

## CI

`ci.yml` runs the same `test lint assembleDebug` matrix on every push/PR;
`release.yml` re-runs tests before publishing APKs on tags.
