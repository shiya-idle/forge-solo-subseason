# Fern-compatible Escher Dimension — Minecraft 26.3

This directory contains the ready-to-install Escher datapack ZIP and the repeatable migration tooling used to produce it. The pack keeps the upstream `tid:` namespace and is intended to be installed alongside the matching Forge Solo Subseason Fabric JAR attached to the [MC 26.3 Escher/Fern GitHub release](https://github.com/shiya-idle/forge-solo-subseason/releases/tag/mc26.3-escher-fern-20261116).

## Files

- `escher-fern-compatible-mc26.3.zip` — ready-to-install datapack; `pack.mcmeta` is at the ZIP root.
- `port_escher_26_3.py` — repeatable schema and command migration, Fern compatibility adjustments, and active function-reference audit.
- `escher-infernal-upstream-recovery-26.3.zip` — original helper bodies recovered from the official upstream Git history; required beside the migration script.

The migration script accepts two arguments: an extracted upstream source directory and an empty destination directory. Run it from the directory containing both the script and recovery overlay, for example:

```bash
python3 port_escher_26_3.py /path/to/extracted-escher-v1.0.12 /tmp/escher-26.3-output
```

The port retains the upstream `credits.txt` and README inside the datapack. The reconstructed helpers are documented in [`FUNCTION-REPAIRS-26.3.md`](../../docs/FUNCTION-REPAIRS-26.3.md); recovered upstream helpers are attributed to Zungrysoft and their source commits in the overlay manifest.

## Compatibility and validation

The port targets Minecraft Java 26.3 / datapack format 121 and the Fern Fabric mod in the release. A combined Fabric 26.3 server cold boot registered the `tid:` dimensions; the active function audit found zero unresolved targets, and representative mob, hallway, and station calls ran without parser errors. Full connected-player progression, room connectivity, and boss gameplay remain staging-test items. See the [runtime report](../../docs/RUNTIME-VALIDATION-26.3.md) and [installation guide](../../docs/INSTALL-FERN-ESCHER-26.3.md).

The datapack sets `keep_inventory false` so Fern's death-drop logic works and does not overwrite the main-world spawn. It retains a high upstream `max_command_sequence_length`; stage and monitor startup/tick performance before public production use.
