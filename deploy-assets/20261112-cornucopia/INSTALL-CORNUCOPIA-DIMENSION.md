# Fern Cornucopia landing/trade dimension bootstrap

This package adds the custom Java dimension `fern:forge_cornucopia` as a separate datapack. It does not replace the active overworld, edit any existing world files, or contain a finished Cornucopia map. The generated terrain is a flat plains floor so the new route and trade-world rules have a destination to target; a built, openly licensed landing map is still a separate import.

## Install on the AWS Ubuntu server

1. Make a backup and stop the server:

   ```bash
   sudo systemctl stop fern-minecraft.service
   cd ~/fern-server
   tar -czf "$HOME/fern-world-backup-before-cornucopia-$(date +%Y%m%d-%H%M%S).tar.gz" world
   ```

2. Copy `world/datapacks/fern-cornucopia-trade` from this archive into the server's active world datapacks directory. If `level-name` in `server.properties` is not `world`, substitute the actual directory:

   ```bash
   mkdir -p ~/fern-server/world/datapacks
   cp -a world/datapacks/fern-cornucopia-trade ~/fern-server/world/datapacks/
   ```

   Do not delete or replace the existing `fern-arenas` pack.

3. Install the rebuilt Fern Fabric mod JAR alongside the existing server mod and restart:

   ```bash
   sudo systemctl start fern-minecraft.service
   ```

   A full stop/start is required for a new dimension; `/reload` is not sufficient. Then test with:

   ```text
   /execute in fern:forge_cornucopia run tp @s 0 80 0
   /waypoint cornucopia
   ```

The dimension path is `forge_cornucopia`, which is the name the mod's trade/pickup rules recognize. The design/map itself is intentionally not included here: research found public full-world candidates marked All Rights Reserved, so they do not meet the open-license requirement. Don't publish or redistribute those maps without creator permission. A free/open map must be selected or authored before calling this a finished landing world.
