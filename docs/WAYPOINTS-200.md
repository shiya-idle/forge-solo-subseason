# Fern public waypoints and Escher entry

Fern exposes 202 public waypoint entries so players can reach the dedicated PvP, class-change, Combiroll forging, Cornucopia landing/trade, and Escher dungeon destinations without operator-only teleport commands.

The allocation is 70 PvP waypoints, 65 class-change waypoints, 65 Combiroll forging waypoints, one Cornucopia entrance, and one Escher dungeon entrance. Names are deterministic and easy to type: `pvp-001` through `pvp-070`, `class-001` through `class-065`, `forge-001` through `forge-065`, plus `cornucopia-001` and `dungeon-001`.

Players use:

```text
/waypoints
/waypoints pvp
/waypoints class
/waypoints forge
/waypoints cornucopia
/waypoints dungeon
/waypoint pvp-001
/waypoint class-001
/waypoint forge-001
/waypoint cornucopia
/waypoint dungeon
/fern return last
```

The waypoints are public and have no permission requirement or teleport fee. They are generated inside the configured arena bounds and are server-authoritative. Bedrock players can use the same commands through Geyser; no client-side waypoint mod is required.

The destination worlds must be created or imported first. The service searches the loaded server dimensions by their configured path, so it works with either a namespaced custom dimension or an imported world whose dimension path is `forge_pvp_arena`, `forge_class_arena`, `forge_combiroll_arena`, `forge_cornucopia`, or Escher's `tid:layer_1`. If a world is missing, the player receives a clear message and no world is created automatically.

The new forging destination is configured as `forge_combiroll_arena`. It is a peaceful, build-enabled arena with claims disabled and PvP/skill damage disabled. The existing PvP and class-change rules remain in their respective configured sections.

`fern:forge_cornucopia` is the landing/trade destination. The mod marks carried stacks with owner metadata, prevents another player's pickup/use outside Cornucopia, and transfers stack ownership when another player picks it up inside Cornucopia. Death-drop stacks are owner-locked and reset to unlimited lifetime, and the dead player receives the drop coordinates. A prebuilt Hub world converted from NetherGamesMC's CC BY 4.0 asset archive is available in the separate Cornucopia Hub bundle; its attribution and import helper are included. Escher integration adds `/waypoint dungeon` to `tid:layer_1` at `(0, 55, 0)`, an Escher Delver daily monster quest, and `/fern return last` for return to the saved Overworld location. The compatible Escher pack preserves the existing Overworld spawn and disables keep-inventory so Fern's death-drop behavior remains active.
