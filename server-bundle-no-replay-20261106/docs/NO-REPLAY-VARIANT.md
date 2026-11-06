# Fern no-replay variant

This bundle is the production Fabric server bundle without Replay Mod, Flashback, FernReplayCapture, or any recorder component.

The included Fabric mod is the same verified server-authoritative gameplay mod used by Fern. The JAR was checked as a ZIP archive and contains no class or resource path matching `replay`, `flashback`, or `recorder`.

The separate `/tmp/fern-replay-capture` Paper plugin project is not included and must not be copied into this Fabric server's `mods/` directory.

This variant preserves skills, HUD payloads, claims, thirst, vendors, Combirolls, waypoints, world-border configuration, Java resource-pack support, and Bedrock pack support.
