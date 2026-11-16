# Escher function repairs for Minecraft 26.3

The official source is [Zungrysoft/The-Escher-Dimension](https://github.com/Zungrysoft/The-Escher-Dimension). The release archive omitted several functions that its live callers use.

## Recovered or restored from existing assets

- Infernal checks, factory selection, sewer room selection, and sewer outflows were recovered from upstream history (commits `df29773a2b6b`, `cc4c9769727c`, `39e66d9665b0`, `edcb8eb8f45a`, and `9a3f5a3931c3`).
- The seven `sand1/orient_room_*` helpers reuse the matching `sand2` transforms. Both room families have 11×11 X/Z templates, so the saved origin offsets remain valid.
- The station straggler caller is redirected to the existing `tid:dungeon/station/generate_straggler_build` function; the caller had omitted the `station/` path segment.

## Reconstructed from in-pack evidence

- `sand1`/`sand2` east and south hallway builders reflect the existing west and north structure templates across the doorway plane; their original structures are 7×7×5 and 5×7×7.
- `dungeon/test/check_up` applies the test dungeon's room retry/termination guard; the test pack has no dedicated vertical stair selector.
- `custom_mobs/war_pig` routes to the existing four variants with a geometric tier roll (50%, 25%, 12.5%, 12.5%).
- `misc/soul_lantern` places a floor-supported soul lantern at the selected decoration cell. No upstream body for this helper was present in reachable history.

Commented-out legacy calls are listed separately in the validation report; they are not executed by Minecraft. The reconstructed behaviors should be play-tested in staging.
