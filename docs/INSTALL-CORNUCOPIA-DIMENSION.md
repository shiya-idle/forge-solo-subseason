# Fern Cornucopia landing/trade dimension

This package adds the custom Java dimension `fern:forge_cornucopia` as a separate datapack. The dimension bootstrap is a flat-plains fallback; it does not replace the active overworld or contain the map itself. A built, openly licensed Hub world is now available as a separate Java 26.3 import bundle.

## Install the dimension bootstrap on AWS Ubuntu

1. Make a full backup and stop the server:

   ```bash
   sudo systemctl stop fern-minecraft.service && cd ~/fern-server && tar -czf "$HOME/fern-world-backup-before-cornucopia-$(date +%Y%m%d-%H%M%S).tar.gz" world
   ```

2. Copy `world/datapacks/fern-cornucopia-trade` into the active world's datapack directory, replacing `world` if `level-name` differs. Keep the existing `fern-arenas` pack.

3. Install the rebuilt Fern Fabric mod JAR alongside the existing server mod, then restart:

   ```bash
   sudo systemctl start fern-minecraft.service
   ```

   A full stop/start is required for a new dimension; `/reload` is not sufficient.

## Use the built Hub map

The recommended open-content option is NetherGames' `Lobby/worlds/Hub`, released under CC BY 4.0. The Java 26.3 converted world, required attribution/license files, and an AWS Ubuntu import helper are in the [Cornucopia Hub import bundle](IMPORT-CORNUCOPIA-HUB-26.3.md). Follow that guide to copy its chunks into `fern:forge_cornucopia`; the importer requires the dimension bootstrap and refuses to run against a running server.

After import, test from an in-game operator account:

```text
/execute in fern:forge_cornucopia run tp @s -25 42 -21
/waypoint cornucopia
```

The Hub save passed a standalone Java 26.3 startup test. Chunker does not convert most entities or structure data, so inspect it and recreate any missing NPCs or technical structures. The chunk import into the Fern custom dimension still needs a visual in-game check. The map does not implement trade rules; those remain controlled by the Fern mod and Cornucopia datapack.
