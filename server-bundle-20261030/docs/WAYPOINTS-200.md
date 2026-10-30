# Fern public waypoints

Fern now exposes 200 public waypoint entries so players can reach the dedicated PvP, class-change, and Combiroll forging instances without operator-only teleport commands.

The allocation is 70 PvP waypoints, 65 class-change waypoints, and 65 Combiroll forging waypoints. Names are deterministic and easy to type: `pvp-001` through `pvp-070`, `class-001` through `class-065`, and `forge-001` through `forge-065`.

Players use:

```text
/waypoints
/waypoints pvp
/waypoints class
/waypoints forge
/waypoint pvp-001
/waypoint class-001
/waypoint forge-001
```

The waypoints are public and have no permission requirement or teleport fee. They are generated inside the configured arena bounds and are server-authoritative. Bedrock players can use the same commands through Geyser; no client-side waypoint mod is required.

The destination worlds must be created or imported first. The service searches the loaded server dimensions by their configured path, so it works with either a namespaced custom dimension or an imported world whose dimension path is `forge_pvp_arena`, `forge_class_arena`, or `forge_combiroll_arena`. If a world is missing, the player receives a clear message and no world is created automatically.

The new forging destination is configured as `forge_combiroll_arena`. It is a peaceful, build-enabled arena with claims disabled and PvP/skill damage disabled. The existing PvP and class-change rules remain in their respective configured sections.
