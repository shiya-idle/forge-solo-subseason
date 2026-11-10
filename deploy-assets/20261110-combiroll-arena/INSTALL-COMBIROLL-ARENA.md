# Fern Combiroll arena dimension

This package creates the missing Java dimension:

`fern:forge_combiroll_arena`

It creates a flat, empty arena surface. The Japan lobby schematic is separate and must be pasted into this loaded dimension with WorldEdit.

## Install

Stop the server, then copy the included datapack into the active world. Replace `world` below if `level-name` in `server.properties` is different.

```bash
sudo systemctl stop fern-minecraft.service
mkdir -p ~/fern-server/world/datapacks
cp -r /tmp/fern-combiroll-arena/world/datapacks/fern-arenas-combiroll ~/fern-server/world/datapacks/
sudo systemctl start fern-minecraft.service
```

A full restart is required; `/reload` is not enough for a new dimension.

## Test in Minecraft chat

```text
/execute in fern:forge_combiroll_arena run tp @s 0 80 0
```

or:

```text
/waypoints forge
```

## Paste the lobby

After teleporting into the dimension, use WorldEdit:

```text
//schem load by-kubj-lobby-japan-free
```

```text
//paste -preview
```

If the position is correct:

```text
//paste -a
```

The `-a` option skips air blocks. Use `//undo` if the position is wrong. The dimension datapack does not replace the main world and does not include `level.dat`.
