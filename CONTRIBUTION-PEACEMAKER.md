# Peacemaker contribution: Tomats economy and player trading

Date: 2026-11-23

## Implemented

- Added the Fabric-side Tomats market catalog, bounded NPC price movement, treasury-funded buybacks, stock tracking, expiring quotes, and per-player daily limits. The existing integer-centavo ledger and reward values remain unchanged; the market displays TMT to two decimal places.
- Added `/market quote`, `/market buy`, and `/market sell` using Minecraft 26.3's namespaced item-identifier parser, plus a regression test for `minecraft:cobblestone`.
- Added a persistent Fabric Tomat ledger and a two-player trade flow with staged item/TMT offers, revision-bound ready/confirm, server validation, cancellation and expiry handling, and a shared trade panel.
- Kept this work separate from real-money payments and from the existing Paper ledger. Emerald migration was not included.

Key implementation files include `platform-fabric/src/main/java/com/forgemagic/fabric/TomatEconomyEntrypoint.java`, `TomatMarketService.java`, `TomatLedger.java`, `PlayerTradeService.java`, `EconomyInventoryOps.java`, `EconomyPriceRegistry.java`, and `EconomyTradeRules.java`; the UI files are `platform-fabric/src/client/java/com/forgemagic/fabric/client/TomatTradeClient.java` and `TomatTradeScreen.java`. The catalog is `platform-fabric/src/main/resources/forge-market-catalog.csv`.

## Build and verification

`./gradlew test :platform-fabric:build` passed, including the namespaced-identifier regression test. `git diff --check` passed.

The rebuilt Fabric JAR is `platform-fabric/build/libs/platform-fabric-0.1.0-SNAPSHOT.jar`, SHA-256 `5ae05c755a3d707ee062bad019a846b0220881edc3d5473b86f4829650c0fc06`.

## Disposable-server QA status

On an isolated Java 26.3 clone, the exact JAR passed namespaced `/market quote`, centavo/TMT formatting, stock-shortage rejection, a one-item buy, per-player netherite buy caps, quote-expiry rejection without stock/audit changes, and sell-limit rejection. Trade checks passed for the centavo precision boundary, offer edits invalidating Ready/Confirm, same-revision confirmation transferring exactly once, and cancellation leaving inventory and balances unchanged.

A successful sell has not yet been confirmed. One fixture was owner-bound; a second test's item became owner-tagged shortly after being taken from a chest, and no sale response or ledger change was captured. The `[SELL ONE]` panel click and end-to-end GUI/network interactions remain untested. A final isolated sell-path retest is still in progress.

No live server or shared QA world was changed, no Emerald migration was performed, and no commit was made.