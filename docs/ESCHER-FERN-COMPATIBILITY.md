# Fern × Escher Compatibility — Minecraft Java 26.3

This integration pairs Forge Solo Subseason on Fabric with the Escher Dimension datapack migrated from its 1.16.5 release. The datapack keeps its `tid:` namespace; Fern’s `forge_*` arenas and `fern:forge_cornucopia` remain separate.

## Integrated behavior

- `/waypoint dungeon` and `/waypoint escher` enter `tid:layer_1` at `(0, 55, 0)`. `/waypoints dungeon` filters to that entry. If the dimension is not loaded, Fern reports that it is unavailable rather than creating a world.
- When the player uses the waypoint from the Overworld, Fern saves the prior location. `/fern return last` returns there; `/fern return spawn` returns to the main-world spawn.
- The daily **Escher Delver** quest counts 10 monster kills in any `tid:` dimension and rewards 16 emeralds. Its progress is stored separately from the existing zombie hunt and uses the existing daily UTC reset. Rewards are owner-bound outside Cornucopia.
- Fern’s item ownership/death-drop behavior remains active in all Escher dimensions. The compatibility datapack sets `keep_inventory false`, so Fern can create permanent owner-locked death drops and send their coordinates.
- Escher’s upstream `setworldspawn 0 55 0` was removed so it cannot relocate the Fern Overworld spawn. Other unrelated global gamerule changes were removed; the upstream command-sequence limit remains for procedural generation.
- Fixed Fabric mixin package isolation: the main mod entrypoint is no longer inside the Mixin-owned package, and the client HUD mixin resides under `com.forgemagic.fabric.mixin.client`.

## Validation

The project was built with Temurin Java 25 using `:platform-fabric:build`; the repository `test` task completed successfully. A Fabric Loom `runServer` test loaded the mod and compatible Escher datapack together on Minecraft 26.3. Fern initialized, the Escher `tid:` dimensions registered, the server reached `Done`, and a live `/gamerule keep_inventory` query returned `false`. A recursive audit found **zero unresolved active `tid:` function targets**. Representative calls to the War Pig selector, south/east hall builders, and station straggler execution function ran without command errors; the disposable server stopped cleanly.

The upstream v1.0.12 archive omitted active function bodies. The port recovers historical upstream implementations and reconstructs missing helpers from existing structures and sibling logic; seven absent references remain only in comments and are inactive. See `FUNCTION-REPAIRS-26.3.md` and `PORT-VALIDATION-26.3.md` in the release assets. This was not a connected-player playthrough: waypoint teleport/return, quest claiming, full room connectivity, boss progression, and long-duration performance remain to be tested in staging.

## Deployment checklist

1. Back up the active world, `mods/`, and server configuration.
2. Use Minecraft 26.3, Fabric Loader 0.19.5 or newer, Java 25, and `enable-command-block=true`.
3. Install the matching Fern Fabric JAR under `mods/` and the compatible Escher datapack at the active world's `datapacks/` root.
4. Fully restart the server to register dimensions; `/reload` alone is not sufficient for new dimensions.
5. Test `/waypoint dungeon`, `/fern return last`, `/quests`, death drops, and at least one complete dungeon route in a staging copy.

Escher is distributed with its upstream `credits.txt` and original README preserved. Follow the upstream notices included in the pack.
