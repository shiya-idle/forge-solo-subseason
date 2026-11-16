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
