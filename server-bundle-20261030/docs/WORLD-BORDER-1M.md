# Fern 1,000,000 × 1,000,000 world border

This policy sets a square world border with a **1,000,000-block diameter**, centered at `0, 0`. It does not pregenerate the entire area. Chunks are generated on demand as players explore inside the border.

Run these commands once from the Minecraft server console:

```text
worldborder center 0 0
worldborder set 1000000
worldborder damage buffer 5
worldborder damage amount 0.2
```

The border is a gameplay boundary. It does not delete chunks that were generated outside the border in the past. Make a backup before applying it to an existing world.

Do not run `chunky` against the entire border. A 1,000,000 × 1,000,000 square is approximately 3,906,250,000 chunks, which is not compatible with a 50 GiB disk. Use a small staged radius around spawn instead, measure disk growth, and expand gradually.

For a first controlled pre-generation pass:

```text
chunky world minecraft:overworld
chunky shape circle
chunky spawn
chunky radius 2000
chunky start
```

Repeat for other dimensions only after checking disk space and server tick time.
