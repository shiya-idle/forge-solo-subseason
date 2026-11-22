# Shen Wan’er QA contribution — 2026-11-23

Compiled from isolated Java runtime checks through 01:11 +08. This note records testing and review only; I did not change mod source, a live server, shared QA worlds, real accounts, or eligibility data.

## v5 waypoint hub — Java keyboard and route checks

Test environment: fresh disposable Minecraft 26.3 server/client, Java 25, Fabric Loader 0.19.5, Fabric API 0.165.0+26.3. The test server was loopback-only. The v5 JAR and datapack matched the supplied hashes:

- JAR `65fa15a84d7915a1050c628f3ec7077b3e058a6d7a51f4ded46595ac121bd759`
- Datapack `7d03ce7aaa11af5728404d501cf6b97e3713227dafe5c85d7d4f6d5e54070812`

`/waypoints hub` arrived at `(0,64,0)` in `fern:forge_waypoint`. Each gate was then tested separately by starting outside its frame and crossing with keyboard input; RCON was only used to observe dimension and position.

| Gate | Destination | Arrival | Result |
|---|---|---:|---|
| Escher / Dungeon | `tid:layer_1` | `(0.5,55,0.5)` | One activation; no bounce or repeat through an approximately 10-second idle check. |
| PvP | `fern:forge_pvp_arena` | `(0.5,65,0.5)` | Stable through a 10-second idle check. |
| Class | `fern:forge_class_arena` | `(-31.5,80,-31.5)` | Stable through a 10-second idle check; needed one extra alignment step. |
| Forge | `fern:forge_combiroll_arena` | `(-63.5,80,-63.5)` | Stable through a 10-second idle check. |
| Cornucopia | `fern:forge_cornucopia` | `(0.5,80,0.5)` | Stable through a 10-second idle check. |

Temporary flat dimension fixtures were used for the destinations, so these passes verify gate entry, routing, and short-term stability—not the real destination maps. A separate dispatch pass confirmed all five configured destination IDs, including a previously unloaded Forge landing chunk loading after arrival. A vanilla Overworld↔Nether round trip outside the hub also worked after vanilla portal blocks were placed inside the obsidian frame; flint-and-steel ignition was not tested. Bedrock remains untested.

Evidence: `/workspace/minecraft-test-results/waypoint-hub-v5-player-test-20261122T2249+0800/results/` and `/workspace/minecraft-test-results/waypoint-hub-v5-four-gates-keyboard-20261123T0052+0800/results/`. Escher arrival screenshot: `/workspace/minecraft-test-results/waypoint-hub-v5-dungeon-keyboard-20261122T2333+0800/results/dungeon-idle-destination.png`.

## Tomat market and player-trade QA

### First market/trade build

The first build JAR SHA-256 was `e11dc92f7ac36b1f9ce55fd505920be9113c6f80ef41ceefe9c7c16b593b1908`.

- The market list displayed two-decimal TMT prices consistent with integer-centavo catalog entries. Examples: Diamond base 8.00, NPC buy 10.40, NPC sell 5.60; Netherite Ingot base 60.00, buy 78.00, sell 42.00.
- Found a namespaced-item parser blocker: `/market quote minecraft:cobblestone` rejected the colon because the item argument was registered as a Brigadier `word`.
- Player trades accepted TMT 0.01 and rejected 0.001. Editing an offer after both players Readied caused Confirm to fail. Confirming the same unchanged revision from both players committed exactly one centavo once.
- Cancelling after both players Readied preserved the staged items and balances and created no completed-trade row.
- Market transactions and trade flows were not fully player-UI tested; some commands were run via the disposable server console as connected test-player identities because client chat input was unreliable. GUI/network-panel interactions remained untested.

### Parser-fix build

The parser-fix JAR SHA-256 was `5ae05c755a3d707ee062bad019a846b0220881edc3d5473b86f4829650c0fc06`.

- `/market quote minecraft:cobblestone` succeeded through player chat and returned its full ID, price, stock, and quote version.
- A 3-item buy against stock 2 was rejected without changing stock or balances. A 1-item buy succeeded for TMT 0.03 and reduced stock from 2 to 1.
- Netherite’s one-item daily buy cap blocked the same player’s repeat purchase while allowing another player’s purchase. Reusing an expired cobblestone quote was rejected without another stock or audit change. The daily sell-limit rejection was observed for both test players.
- These transaction checks used console execution as the test players because player-chat attempts produced no transaction reply or effect. The marketplace GUI/network panel was not tested.

### Sell-path status

A follow-up tried an ordinary cobblestone stack taken from a chest through the in-game GUI. It was untagged at tick 10060 and owner-tagged by tick 10081; a player-chat sale attempt produced no captured response, so it is unknown whether it reached validation before the binding pass. A prior sale attempt using an NPC-purchased bound stack was rejected as protected/marked; that does not verify a successful sale.

The earlier unbound-stack retest ended with wallet TMT 0.00, market treasury 10,000,000 centavos, cobblestone sold 0, and stock 4,096. The focused post-gate follow-up completed in a disposable clone using the parser-fix JAR with the requested SHA-256 (`5ae05c755a3d707ee062bad019a846b0220881edc3d5473b86f4829650c0fc06`). After mining a diamond through the player UI, the test player held an owner-tagged diamond. Clicking `[SELL ONE]` showed “Protected or marked items cannot be sold to the NPC market.” The diamond remained in inventory; no sale posted: diamond sold remained 0, stock remained 8, the captured ledger showed zero credits and debits, and no trade-audit file existed. The clone was at 20 TPS and stopped by 01:22:39 +08. This verifies the UI click reached protected-item rejection, but the test did not establish an eligible item or verify a successful sale; successful selling remains unverified.

Evidence for the first build: `/workspace/minecraft-test-results/tomat-market-trade-qa-20261122T2250+0800/`. Parser-fix evidence: `/workspace/minecraft-test-results/market-parser-postfix-qa-20261122T2344+0800/`. Earlier sell retest evidence: `/workspace/minecraft-test-results/market-sell-unbound-retest-20261123T0003+0800/`. Focused post-gate sell-path evidence: `/workspace/minecraft-test-results/sell-path-check-20261123T0111+0800/`.

## Earlier Java UI and feature checks

Previous isolated Java 26.3 QA found a `/waypoints` help/command mismatch, a blank NOVICE loadout on the final JAR, and a HUD overlap in an earlier layout build. A later exact HUD-fix build passed the 800×600 status-card and `/skills panel` chat-layout check. Duplicate-domain/skills checks passed; class arena and Escher dungeon travel worked. Class unlock and live skill effects were not tested because the 72,000-tick eligibility threshold was not reached. These results do not count as Bedrock coverage.

Relevant evidence includes `/workspace/minecraft-test-results/compact-hud-chat-fix-20261122T1835+0800/client/compact-hud-skills-panel-clean-800x600.png` and `/workspace/minecraft-test-results/duplicate-domain-skills-qa-20261122T2024+0800/artifacts/fern-forge-solo-subseason-0.1.0-mc26.3-qa.jar`.

## Monetization review contribution

Mojang’s official [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) and [EULA](https://www.minecraft.net/en-us/eula) were reviewed. The safe design recommendation is to keep Tomats strictly in-game with no real-world value, cash-out, or conversion; any trade fees should remain in the in-game treasury. Real-money subscriptions should be priced and accounted for separately and should avoid gameplay perks that grant competitive advantage; cosmetics/noncompetitive perks are safer. Purchase information and history requirements should be addressed. This is product-risk guidance, not legal advice; local tax/accounting questions need qualified professional advice.

Source notes: `/workspace/minecraft-monetization-guidelines-notes-2026-11-22.md`.
