# Worklog

---
Task ID: 1
Agent: main (Super Z)
Task: Read blueprint «چقدر؟» and initialize fullstack environment

Work Log:
- Read /home/z/my-project/upload/چقدر.txt (1293 lines) — full spec for «چقدر؟» market tracker
- Blueprint targets native Android (Kotlin/Compose); user asked to switch to fullstack web env to see code + preview
- Decision: build «چقدر؟» as Next.js 16 web app (PWA-style) faithfully adapting the blueprint: fa/en + RTL, light/dark, home dashboard, markets explorer, watchlist, converter, alerts, charts, offline cache
- Data honesty (blueprint rule): crypto = live CoinGecko public API; Iranian markets (currency/gold/coins/stocks) = clearly-labeled DEMO provider with realistic baselines + persistent demo banner; no fake TGJU endpoints
- Initialized fullstack env via init script; installed jalaali-js

Stage Summary:
- Blueprint understood; web adaptation strategy fixed
- Next project at /home/z/my-project, dev server on :3000
