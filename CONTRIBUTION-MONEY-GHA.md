# Money Gha contribution: team finance and Tomats economy

Date: 2026-11-23

## Finance and economy design contribution

This note records the finance recommendations contributed to the Forged Magics economy discussion. It is design guidance, not a claim that the remaining market transaction or UI tests have passed.

- Keep Tomats entirely in-game and separate from real-money checkout. Do not assign a cash exchange rate to Tomats, sell Tomats for cash, cash them out, or route a portion of in-game sales to a bank account. Mojang's [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) say server virtual currencies must have no real-world value and cannot be cashed out, used or transferred across other servers, or converted into real-world currency.
- Preserve the existing integer-centavo ledger and current bounty/reward amounts. Display Tomats (TMT) to two decimal places. For example, a cobblestone base value of TMT 0.02 is 2 centavos; do not multiply existing rewards or payouts by 100.
- Keep the NPC as a price reference and player-to-player prices freely negotiated. The item catalog should use namespaced registry IDs, base value, NPC buy/sell enablement, opening buy/sell quotes, stock targets/caps, daily limits, and acquisition/crafting notes. NPC buy/sell should be understood from the NPC's perspective: buy is what it pays the player; sell is what the player pays the NPC.
- Use fractional precision internally for dynamic quote movement, then round executable quotes to centavos while keeping the NPC buy quote below its sell quote. The aligned v1 direction is a 15-minute update cadence, a maximum move of 0.5% of base value per update, an 80–120% of base band, and 10% daily drift of the remaining gap back toward base.
- Fund NPC buybacks from a finite in-game treasury, without automatic minting/refill; pause NPC buying if the treasury cannot cover the quote. Keep stock and per-player daily limits visible and enforce them server-side.
- Keep v1 player trades free of a forced transaction tax. If the team later chooses an in-game fee, make it a Tomat sink paid to the in-game treasury and disclose it before confirmation; do not pay it out as real money.
- Track the money systems separately. For Tomats, record amounts minted, spent, transferred, and the treasury balance. For real-money checkout, separately record gross receipts, processor fees, refunds/chargebacks, renewals, and net payouts. No shared exchange formula or ledger.
- Price any permitted cash subscription directly in cash and separately from Tomats. Avoid competitive paid advantages such as 2× power or XP; prefer cosmetic or non-competitive perks and make the contents and price clear before purchase.

## Scope

I contributed the finance guardrails and economy policy alignment for the market/trade work. I did not implement the market or trade code. The code implementation and current QA state are recorded in [`CONTRIBUTION-PEACEMAKER.md`](CONTRIBUTION-PEACEMAKER.md); in particular, a successful NPC sell and end-to-end panel interactions were still unverified in the last report.