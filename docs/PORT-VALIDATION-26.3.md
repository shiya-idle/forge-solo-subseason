# Escher 26.3 static compatibility report

The migration script restores and reconstructs active missing helpers before auditing the resulting datapack.

## Live function-target audit

Static audit found **0** unresolved active `tid:` function targets.

No unresolved active `tid:` function targets found.

## Comment-only legacy references

The pack retains **7** missing target name(s) only inside comment lines. They are inactive and do not prevent execution.

- `tid:dungeon/finale/generate_straggler` — `data/tid/function/dungeon/finale/finale_handler_p30.mcfunction:4`
- `tid:dungeon/infernal/generate_straggler` — `data/tid/function/dungeon/infernal/infernal_handler_p30.mcfunction:4`
- `tid:dungeon/intro/generate_straggler` — `data/tid/function/dungeon/intro/intro_handler_p30.mcfunction:4`
- `tid:dungeon/sin/generate_straggler` — `data/tid/function/dungeon/sin/sin_handler_p30.mcfunction:4`
- `tid:dungeon/test/generate_straggler` — `data/tid/function/dungeon/test/test_handler_p30.mcfunction:4`
- `tid:misc/fusion_pipe_start` — `data/tid/function/dungeon/layer_2/layer_2_handler_p40.mcfunction:19`
- `tid:misc/stronghold2_cave_item` — `data/tid/function/dungeon/stronghold2/cave_scatter.mcfunction:11`

## Runtime smoke validation

The integrated Fabric 26.3 cold boot and representative function calls passed; see `RUNTIME-VALIDATION-26.3.md`.

## Validation boundary

A zero-reference audit, clean startup, and representative smoke calls do not establish every random room layout, boss progression, or long-run performance. See `FUNCTION-REPAIRS-26.3.md` for recovered versus reconstructed behavior, and perform a player-level staging run before production.
