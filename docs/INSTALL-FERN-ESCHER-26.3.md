# Install Fern + Escher on a Fabric 26.3 AWS Ubuntu server

## Important status

The Fern mod and repaired Escher datapack were cold-booted together on a disposable Fabric 26.3 server with Java 25. The recursive audit reports zero unresolved active `tid:` function targets; seven stale target names remain only in comments. Representative mob, hall, and station function calls ran without errors. Full player-driven dungeon progression is not yet validated, so install on a staging copy first.

The Escher init function also retains the upstream `max_command_sequence_length 1000000000` setting. This is a server-wide, very high command-chain limit needed by the upstream procedural-generation design; monitor CPU/memory and assess whether to lower it after gameplay testing. On unprepared dimensions the pack force-loads a 15×15-chunk square per TID dimension; one combined test run logged 6,889 ms / 137 ticks behind during startup. The cause and steady-state cost were not isolated, so measure this on an AWS-sized staging instance before public launch.

## Requirements

- Minecraft Java 26.3, Fabric Loader 0.19.5+, and Java 25.
- `enable-command-block=true` in `server.properties` (required by the upstream datapack).
- The Fabric server must use the same active world directory for the Escher datapack; adjust `WORLD_DIR` if `level-name` is not `world`.
- Back up the world and server files before installation. Stop the server cleanly first.

## Install from the combined bundle

Upload `Fern-Escher-Compatible-MC26.3.zip` to the EC2 server, then extract it to a temporary staging folder. Set the two paths in one shell session; change these example paths to your server and uploaded bundle locations:

```bash
unzip -o "$HOME/Fern-Escher-Compatible-MC26.3.zip" -d "$HOME/Fern-Escher-Compatible-MC26.3"
```

```bash
BUNDLE="$HOME/Fern-Escher-Compatible-MC26.3" && SERVER_DIR="$HOME/minecraft" && WORLD_DIR="$SERVER_DIR/world"
```

With the server stopped, create a backup (adjust the world folder name if needed):

```bash
tar -czf "$HOME/minecraft-before-escher-$(date +%Y%m%d-%H%M%S).tar.gz" -C "$SERVER_DIR" world server.properties mods
```

Install the mod JAR and the datapack ZIP (Minecraft accepts a datapack ZIP directly in `datapacks/`):

```bash
install -D "$BUNDLE/mods/forge-solo-subseason-mc26.3-escher-compatible.jar" "$SERVER_DIR/mods/forge-solo-subseason-mc26.3-escher-compatible.jar" && install -D "$BUNDLE/datapacks/escher-fern-compatible-mc26.3.zip" "$WORLD_DIR/datapacks/escher-fern-compatible-mc26.3.zip"
```

Ensure command blocks are enabled and online authentication is on for a public server; this one-line command updates or appends both settings:

```bash
cd "$SERVER_DIR" && if grep -q '^enable-command-block=' server.properties; then sed -i 's/^enable-command-block=.*/enable-command-block=true/' server.properties; else printf '\nenable-command-block=true\n' >> server.properties; fi && if grep -q '^online-mode=' server.properties; then sed -i 's/^online-mode=.*/online-mode=true/' server.properties; else printf 'online-mode=true\n' >> server.properties; fi
```

Start the server with your existing systemd unit or start script. A full restart is required for custom `tid:` dimensions; `/reload` is insufficient. Check startup logs for Fern initialization and the Escher datapack, then verify:

```text
/gamerule keep_inventory
```

It should report `false`. This setting is intentional: Fern’s death system keeps the dropped items in the world, locks them to the dead player, prevents despawn, and reports their coordinates. The compatible datapack no longer changes the Overworld spawn.

## Player commands

- `/waypoint dungeon` or `/waypoint escher` — enter `tid:layer_1` at `(0, 55, 0)`.
- `/fern return last` — return to the saved Overworld location when the player entered from the Overworld.
- `/fern return spawn` — return to the main-world spawn.
- `/quests` — view the Fern daily zombie hunt and **Escher Delver** objective (10 monster kills in a `tid:` dimension for 16 emeralds).

The Cornucopia item-trading boundary is unchanged: Fern-bound items may only change owners inside Cornucopia; Escher is not a trade zone.

## Validation and rollback

In a staging world, test waypoint entry/return with a connected player, kill and claim the Escher quest, die with Fern-bound items, and traverse each dungeon route. Keep an eye on memory and tick time during generation. Review `PORT-VALIDATION-26.3.md`, `FUNCTION-REPAIRS-26.3.md`, and `RUNTIME-VALIDATION-26.3.md`; current validation covers startup/parser behavior and representative calls, not a complete playthrough.

To roll back, stop the server and remove the Escher datapack ZIP and compatible mod JAR, then restore the world/config backup if the test changed world data. Retain the backup until the staging playthrough succeeds.
