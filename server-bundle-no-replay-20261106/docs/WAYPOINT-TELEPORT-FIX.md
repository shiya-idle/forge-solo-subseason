# Waypoint teleport fix

The waypoint command now accepts direct aliases:

- `/waypoints class` -> `class-001`
- `/waypoints class-001`
- `/waypoint class`
- `/waypoint class-001`
- `/waypoints pvp` -> `pvp-001`
- `/waypoints forge` -> `forge-001`

If the target dimension is missing, the command reports that the destination is not loaded. If Minecraft rejects the cross-dimension teleport, the command reports that failure instead of displaying a false success message.
