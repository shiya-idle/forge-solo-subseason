# Install the built Cornucopia Hub world on AWS Ubuntu

This is a full Java 26.3 world save converted from NetherGames' Bedrock Hub world. It is licensed CC BY 4.0, unlike many free-download maps whose terms prohibit reuse. The converted save successfully started on an isolated vanilla Minecraft 26.3 server. The source spawn is **(-25, 42, -21)**.

## Import it into the existing `fern:forge_cornucopia` dimension

1. Upload and extract the `Cornucopia-Hub-Java-26.3.zip` bundle somewhere under your home directory. For example, if the archive is in `~/Downloads`, this one-line command extracts it:

   ```bash
   mkdir -p ~/fern-server/import && unzip -o ~/Downloads/Cornucopia-Hub-Java-26.3.zip -d ~/fern-server/import
   ```

2. Stop the server and create a full backup of its active world. This single command uses the same service/world layout as the earlier Fern guide:

   ```bash
   sudo systemctl stop fern-minecraft.service && cd ~/fern-server && tar -czf "$HOME/fern-world-before-hub-$(date +%Y%m%d-%H%M%S).tar.gz" world
   ```

3. Run the bundled importer. It checks that the Cornucopia dimension datapack and world exist, refuses to run while the service/server process is active, and creates a backup of the target dimension before replacing its chunk data:

   ```bash
   bash ~/fern-server/import/Cornucopia-Hub-Java-26.3/IMPORT-CORNUCOPIA-HUB.sh ~/fern-server --replace
   ```

   Use `--replace` only if replacing an existing `forge_cornucopia` chunk set. The helper reads `level-name` from `server.properties`; if your server directory or service differs, pass the actual server directory as the first argument and set `FERN_SERVICE` to your service name.

4. Start the server and test the destination before enabling player traffic:

   ```bash
   sudo systemctl start fern-minecraft.service
   ```

   From an in-game operator account, teleport into the map with:

   ```text
   /execute in fern:forge_cornucopia run tp @s -25 42 -21
   ```

   Your existing `/waypoint cornucopia` can remain the player-facing route if it points to this dimension and spawn. The map import does not create or alter trade permissions; those still come from the Fern mod and Cornucopia datapack.

## Conversion limits and license

The source world is [NetherGamesMC/assets — Lobby/worlds/Hub](https://github.com/NetherGamesMC/assets/tree/master/Lobby/worlds/Hub). The creator released the map under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/); the package includes `LICENSE.txt` and an `ATTRIBUTION.txt` notice. Credit NetherGames, link the source and license, and say the map was modified/converted.

Chunker 1.21.0 converted the source from Bedrock 1.26.0 to Java 26.3.0. Chunker states that most entities and structure data do not convert, so re-add required NPCs and any lost technical structures after checking the world. The conversion emitted a warning about a missing `minecraft:info_update` block mapping. Three resulting region files were outside Minecraft's world border and were omitted from this package. The standalone world passed a Java 26.3 boot check; importing its chunks into the custom Fern dimension still needs an in-game visual inspection.
