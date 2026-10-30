# Bedrock pack and one-million-block border setup

## Bedrock resource pack

Geyser does not convert Java resource packs automatically. It accepts a real Bedrock `.mcpack` or `.zip` in its local `packs` directory. For a Fabric installation, copy `forge-solo-bedrock-26.3.mcpack` into:

```text
config/Geyser-Fabric/packs/
```

Restart Geyser or reload it. Bedrock players will be offered the pack when they connect. Geyser documents this local-pack behavior here: https://geysermc.org/wiki/geyser/packs/

If using a remote pack instead, the download URL must return the file directly with the exact `Content-Length` and `Content-Type: application/zip` headers. The pack manifest uses the Bedrock resource-module format described by Microsoft here: https://learn.microsoft.com/en-us/minecraft/creator/reference/content/addonsreference/packmanifest?view=minecraft-bedrock-stable

The pack provides Fern textures and sounds. Live health, mana, and thirst values remain in the Bedrock action bar because the Java Fabric HUD mixin cannot run in a Bedrock client.

## One-million-block border

The Fabric mod now reads the `world-border` section from `forge-solo-skills.yml` and applies a diameter of `1,000,000` blocks centered at `0, 0` to loaded dimensions when the server starts. The setting is:

```yaml
world-border:
  enabled: true
  center-x: 0
  center-z: 0
  diameter-blocks: 1000000
  generate-on-demand-inside-border: true
  pregenerate-entire-border: false
```

This limits where new chunks can be generated and where players can travel. It does not delete terrain that was already generated outside the border. Do not use Chunky to pregenerate the entire border; it is far beyond a 50 GiB disk.
