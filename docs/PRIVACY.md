# Privacy

Cheghadr is a local-first market viewer. This document describes **actual**
behavior — not aspirations.

## What the app sends where

| Data | Destination | When |
|---|---|---|
| HTTP GET requests to market-data endpoints | CoinPaprika, open.er-api.com, Frankfurter, gold-api.com, and tgju.org public HTML pages (currency/gold/coin sections + per-symbol profile pages) when the Iranian-market toggle is on | on launch/foreground refresh, pull-to-refresh, and periodic background sync |
| A CoinGecko demo API key, **if the user enters one** | sent as a request header to CoinGecko only | only when CoinGecko is actually queried |

That is the entire outbound surface. Requests carry no user identifier, no
advertising ID, no analytics, no crash reporter. The app contains **no
trackers and no third-party SDKs beyond the UI/network/database libraries
listed in the version catalog**.

## What stays on the device

- **Room database** (app-private storage): latest quotes, quote history
  observations (capped at one sample per asset per hour, pruned after two
  years), asset metadata, favorites, alerts + trigger state, portfolio
  transactions, widget configurations, search history, provider health.
- **DataStore** (app-private): language, theme, digit style, toman display,
  refresh interval, notification preferences, quiet hours, TGJU toggle + base
  URL (optional mirror), CoinGecko demo key.
- Backups follow `backup_rules.xml` / `data_extraction_rules.xml` — Android's
  default cloud backup of app data is allowed unless the user disabled it
  system-wide; the app never uploads this data itself.

## What the app never does

- No account registration, no sign-in, no cloud sync.
- No location, contacts, microphone, camera, storage, or phone permissions —
  only Internet, network state, notifications, boot (to repaint widgets), and
  vibrate.
- No silent upload of watchlists, portfolio, or usage data anywhere.
- No WebView, so no third-party web tracking surface.

## Deleting your data

Uninstalling the app removes the Room database and DataStore entirely
(including your optional API keys and TGJU mirror URL). Nothing persists
elsewhere.

## Template note for public releases

If you publish a build of Cheghadr, attach this document (updated to reflect
any changes you make) as the store-listing privacy policy. The above is
accurate for the code in this repository at the time of writing; verify it
against your own modifications before republishing.
