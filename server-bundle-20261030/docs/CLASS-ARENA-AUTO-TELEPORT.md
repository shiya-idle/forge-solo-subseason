# Automatic class-arena teleport

A normal player accumulates connected server playtime. After 72,000 connected server ticks, equal to three complete 24,000-tick Minecraft days, the server sends the player to the existing `class-001` waypoint in `forge_class_arena`.

The arrival is persistent. The server records it in `config/forge-player-classes.properties`, so reconnects and restarts do not repeatedly teleport the player. If `forge_class_arena` is not loaded, the teleport is not marked as complete and the server retries after the world becomes available.

After arrival, the player chooses a permanent class with one of:

- `/class choose HEALER`
- `/class choose MINER`
- `/class choose MARKSMAN`
- `/class choose LUMBER`
- `/class choose CRAFTSMAN`

The matching class weapon is granted after selection, only if the inventory does not already contain one.

XUID-bound admin profiles such as Yuanfreces are not sent through the novice class-arrival flow because they already have an assigned class.
