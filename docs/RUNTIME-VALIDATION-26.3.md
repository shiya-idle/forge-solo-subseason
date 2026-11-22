# Escher + Fern runtime validation, Minecraft 26.3

Validation date: 2026-11-16. Environment: Fabric Loom 1.18.4, Fabric Loader 0.19.5, Minecraft 26.3, Java 25.0.5+8, and the Forge Solo Subseason Fabric mod.

## Passed checks

- `:platform-fabric:build` completed successfully.
- The integrated `:platform-fabric:runServer` cold boot reached the server-ready `Done` marker; Fern initialized and all `tid:layer_*` dimensions registered.
- The pack audit found zero unresolved active `tid:` function targets, and the server log contained no datapack/function parse failures.
- Representative smoke calls to `tid:custom_mobs/war_pig`, `tid:dungeon/sand1/build_hall_south`, `tid:dungeon/sand2/build_hall_east`, and `tid:dungeon/station/generate_straggler_execute` ran without command errors.
- The disposable server was stopped cleanly.

## Scope limit

This is startup/parser plus representative-call validation, not a full player-driven dungeon run. Procedural room connectivity, progression, every random branch, balance, and long-run performance still need a staging playtest. Non-blocking unrelated notices observed were vanilla piglin-barter loot-table parameter warnings and Fabric untranslated item-tag warnings.

## Escher random-generation integration patch (2026-11-22)

- The runtime patcher tags all 16 teleporter summon sites and 8 structure portal markers with stable `tid_portal_color_N` identities plus a generic marker tag. Normal routing uses stable tags, and active custom-name selectors are removed. The proposed live migration sweep was withdrawn after the disposable runtime probe below showed that 26.3 does not retain the legacy `Color` field needed to infer a destination safely.
- A per-player `tid_portal_contact` latch is set before cross-dimension transfer and cleared after the player leaves the sea-lantern portal floor, preventing repeat processing while standing on a portal or immediately arriving at another portal.
- A disposable live probe summoned an area-effect cloud using the legacy portal name and `Color:8b`. The entity retained its exact `CustomName`, but the old `Color` NBT was absent (`data get entity @s Color` failed), and calling `tid:main` did not assign `tid_portal_identity_ready`. This explains why the earlier entity-migration probe was inconclusive; automatic migration cannot safely infer the old destination. Existing worlds with already-spawned untagged markers must rebuild those markers from the current tagged structures/functions or manually retag them from a verified destination-color mapping before upgrade. The user's existing QA world was not used or modified.
- All four standard-library regression tests pass. They cover portal helper transformations, stale migration-code removal and the rebuild-required upgrade boundary, stable dispatch, archive validation, repeat-patch idempotence, and the Finale return-pad geometry. They do not substitute for a player walking through a portal.
- The static color-12/13 geometry check confirms the color-13 marker at `(3, 143, 3)` centers its trigger at `(4.5, 142, 4.5)` with radius 3. All 12 generated sea-lantern cross tiles are within 2.24 blocks; even the full 4×4 fill footprint would be within 2.83. Color 12's nominal arrival target `(0, 143, 0)` is 6.44 blocks away, and Wan'er observed `(0.189, 143, 0.5)` at 5.97 blocks, so the bedrock spawn is outside the trigger by design and no spawn-floor repair is indicated. The contact latch clears on non-lantern ground. For the walk-up test, step onto the center lantern at `(4, 141, 4)` (feet at `(4.5, 142, 4.5)`); a world-coordinate lantern at x=12 is not this pad. The player-driven return check remains pending.
- `./gradlew --no-daemon :platform-fabric:build` passed after adding Overworld return-position tracking every 10 server ticks and checking teleport success before reporting a return.
- The last-Overworld-position map is currently in memory and is not persisted across server restarts.
- The rebuilt ZIP passed `unzip -t` and a structural audit: all 16 stable-tag dispatches are present, latch reset precedes dispatch, and executable migration-bridge/readiness selectors and active custom-name portal selectors are absent. A fresh disposable project copy (excluding the existing `platform-fabric/run` QA world) auto-loaded this ZIP, reached `Done`, saved all registered `tid:` dimensions, and shut down cleanly. The log contained no datapack/function parse errors. This validates loading only, not connected-player portal traversal.
- Wan’er’s preceding staging pass succeeded for the Layer 1 landing and `/fern return spawn` / `/fern return last`; keep these as regression checks for the next playtest.

These checks do not include a connected-player portal walk-through. A staging test is still required to confirm the player transfers by walking through, arrives in the right dimension, does not bounce back, and can walk back through. Also rerun the previously passing Layer 1 landing and return-command checks as regressions, alongside procedural room connectivity and random-branch coverage. The existing test save was not changed.

## Hellion return approach floor follow-up (2026-11-22)

- Cai'er’s geometry review rejected the earlier `(2, 182, 0)` candidate: it is outside the active color-1 trigger’s radius. The final guarded repair is at `(-6, 182, -6)` in `tid:layer_hellion`, the bedrock floor directly under the marker at `(-6, 183, -6)`. The active trigger is centered near `(-4.5, 182, -4.5)` with radius 3, so a player standing on this adjacent tile is within range. The tick repair runs after every portal dispatch/build call and replaces the tile with sea lantern only while it is still bedrock; this prevents the initial portal build from overwriting it and leaves the contact latch and routing rules unchanged.
- Regression assertions now check the corrected coordinate, marker/radius relationship, bedrock-only guard, ordering after the portal builders, removal of the earlier out-of-range claim, and repeat-patch idempotence. The change is datapack-only, so the matching Fabric JAR is unchanged. No existing QA save was opened or modified.
- The first isolated Minecraft 26.3 parser run caught an invalid trailing `replace bedrock` argument on `setblock`; `tid:main` did not load in that run, so it is not counted as validation. After removing that argument, a fresh Fabric 26.3 / Loader 0.19.5 world loaded `tid:main` without function parse errors. On that disposable server, setting `(-6, 182, -6)` to bedrock and invoking `function tid:main` resulted in sea lantern, confirmed by a block-state scoreboard check. This is command-level validation only, not connected-player travel.
- A new player-driven two-way portal walk-through on a fresh disposable world is still required to verify the repaired floor in-game and rerun the no-bounce/no-repeat checks; startup or static tests alone do not establish that result.
