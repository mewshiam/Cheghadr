# Data licenses & redistribution considerations

Cheghadr displays third-party market data. This note records what each source's
terms mean for this app; it is **not legal advice**, and the state of each
service should be re-verified before any commercial redistribution.

## Summary table

| Source | License posture for an app like Cheghadr | Obligations honored in-app |
|---|---|---|
| CoinPaprika | Free tier is described as free for personal use; commercial use may require a paid plan. Public API attribution is requested. | Attribution string on quotes/charts; single bulk request per sync; no caching/redistribution beyond the user's device |
| open.er-api.com (ExchangeRate-API open endpoint) | The open endpoint is offered for free without a key; the company sells keyed plans for heavier use. Rates are factual data; the ToS should be reviewed for redistribution clauses. | Attribution; daily-frequency data used as-is; official rates clearly labeled |
| Frankfurter | MIT-licensed service serving ECB reference rates. ECB rates are public-sector information reused under ECB's reuse policy. | Attribution (“ECB reference rates via Frankfurter”); business-daily data used as-is |
| gold-api.com | Markets itself as a free gold/silver price API without a key; terms page should be re-checked for rate/redistribution limits. | Attribution; two requests per sync |
| CoinGecko (optional) | Free/public endpoints have documented rate limits and attribution requirements; the Demo plan (optional user key) relaxes limits. | Attribution; optional user-supplied demo key stored only in DataStore; currently maps no catalog assets |
| TGJU (tgju.org) | Site terms state copying content requires written permission and the documented API is a paid product. Scraping was implemented **at the repository owner's explicit request** for personal, on-device use. | On-device scraping of public pages only (no auth bypass, no CAPTCHA/identity/rate-limit evasion); attribution on every Iranian market quote; toggleable in Settings; owner accepts terms-compliance responsibility |

## Principles encoded in the implementation

1. **No secrets in the repo or APK.** Provider keys (CoinGecko demo key)
   are user-entered at runtime and stored in the app's private
   DataStore; nothing is compiled in. Signing material is injected via
   environment variables in CI only.
2. **Controlled scraping.** The only HTML scraper (tgju.org) was added at the
   repository owner's explicit direction: on-device, public pages, browser-like
   headers, sync-cadence request rate with exponential backoff, full attribution,
   and a settings toggle. No credential use, no anti-bot bypass of any kind.
3. **Attribution travels with the data.** Every quote row, detail screen and
   share text names its provider; the settings screen shows live provider
   health (last success, last error, consecutive failures).
4. **Facts are not relabeled.** Official rates are labeled official; computed
   cross rates are labeled computed; free-market values come from the tgju.org
   scraper and are labeled «بازار آزاد». The converter never mixes a
   USD-quoted crypto price with an IRR-quoted official rate without an
   explicit, disclosed conversion step.
5. **Device-local caching only.** Provider responses are cached in the app's
   private Room database on the user's device (2-year retention for
   observation history). Nothing is re-published by the app.

## Font

Vazirmatn is bundled under the SIL Open Font License 1.1 (`LICENSE-OFL.txt`).
The license permits bundling and redistribution in unmodified form with the
license file preserved.
