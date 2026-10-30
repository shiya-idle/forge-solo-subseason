# Fern Fabric server/client audit — 2026-10-29

## Direct conclusion

The four performance mods are suitable candidates for Minecraft 26.3 Fabric:

- **Lithium**: server-side game-logic optimization; its current project page lists Minecraft 26.3 and Fabric support.
- **FerriteCore**: memory-use optimization; its current project page lists Minecraft 26.3 and Fabric support.
- **Krypton**: network-stack optimization; its current project page lists Minecraft 26.3 and Fabric support.
- **spark**: profiling and diagnostics, not a permanent gameplay optimization. Install it only after checking the exact Fabric 26.3 build and remove or restrict it in production if its commands or sampling are not needed.

**Chunky** is also listed for Minecraft 26.3 and Fabric. It should be installed server-side and used in controlled batches, not pointed at a 1,000,000 by 1,000,000 block area.

Sources: [Lithium](https://modrinth.com/mod/lithium/versions), [FerriteCore](https://modrinth.com/mod/ferrite-core/versions), [Krypton](https://modrinth.com/project/fQEb0iXm), [Chunky](https://modrinth.com/project/fALzjamp), and [Fabric networking](https://docs.fabricmc.net/develop/networking).

## Critical client-mod limitation

A normal Fabric server can send and require a **server resource pack**, but it cannot reliably install a Java client mod into an ordinary player's launcher at join time. The server cannot write into the player's `.minecraft/mods` directory. A third-party automatic modpack installer can offer that workflow, but it is a separate client-side installer and must be trusted, versioned, and maintained.

The safe supported delivery model is:

1. Publish one signed/versioned client modpack containing Fabric Loader, Fabric API, the Fern client companion, and the exact required client libraries.
2. Give players an official launcher profile or Modrinth/Prism/CurseForge-compatible import package.
3. Keep the server resource pack enabled with `require-resource-pack=true` for textures, fonts, sounds, and overlays.
4. Add a Fern handshake. The server sends a challenge after login; the client companion answers; the server disconnects clients that do not answer with a clear installation URL. This enforces the mod without pretending that the server downloaded it.

The handshake should use Fabric play payloads registered on both sides. It must not trust a client-supplied display name or mod list by itself. The server should require a signed build identifier or a protocol version that the official companion knows how to answer.

## Current implementation status

### Implemented or substantially present

- Fabric 26.3 project structure with Java 25 toolchain settings.
- Server-authoritative skill effect engine and PvP target resolution.
- Mana, thirst, vendor modifiers, claims persistence, purge catalog, and extreme-nightmare configuration classes.
- Resource-pack configuration and generated `server.properties` template.
- Client HUD mixin that cancels the vanilla health rendering and draws a basic health bar.
- Visual resource-pack build script and resource-pack archive.
- Chunky is compatible with the selected Minecraft/Fabric version and can be added as an external server mod.

### Partial or not finished

1. **Client HUD is incomplete.** The current client code only reads local vanilla health and draws a basic bar. It does not yet render server-supplied mana, thirst, level, class, bound skills, cooldown rings, quality presets, cast identifiers, damage flashes, or arena state.
2. **Client companion metadata is incomplete.** The mod has a client mixin but no dedicated client initializer or client/server payload protocol for HUD state and required-client verification.
3. **Automatic client installation is not implemented and cannot be provided by the server alone.** A distributable client pack and a join-time handshake are still required.
4. **The real resource-pack URL is not configured.** `forge-solo-visuals-server.properties` still contains `YOUR-HOST.example`; upload the zip to HTTPS and replace the URL and SHA-1 before production.
5. **The declared class-change and PvP worlds are configuration destinations.** The YAML explicitly says the worlds must be created/imported first. The mod does not silently create or reset those world data sets.
6. **The four performance mods are not bundled into the Fabric output.** They must be downloaded as exact version-pinned jars and placed in the server `mods` directory. They should be tested one at a time with the Fern mod before enabling all four.
7. **The build is currently blocked in this environment by the runtime.** Gradle is using Java 21, while this project requires Java 25. The code has not passed a fresh full build until Gradle is run with a Java 25 `JAVA_HOME`.
8. **The 8 GiB server needs a heap budget.** Do not assign all 8 GiB to Java. Start with a 5–6 GiB heap and leave at least 2 GiB for the operating system, native memory, file cache, RCON, the control agent, and profiling.
9. **The replay/Flashback capture system is not part of the server mod and should not be installed on the production VM until its client and recorder workflow is separately verified.**

## Why a 1,000,000 × 1,000,000 pre-generated area is not viable

A square of that size contains 1,000,000,000,000 blocks. At 16 blocks per chunk, it is approximately 62,500 chunks on each side, or **3,906,250,000 chunks** before considering borders, extra dimensions, entities, structures, region-file overhead, and backups.

That is many orders of magnitude beyond a 50 GiB disk. Even if a hypothetical chunk averaged only 50 KiB, the raw chunk data would be roughly 182 TiB. Real modded terrain can be larger. Pregeneration would also consume a very large amount of CPU time and disk I/O and would compete with player ticks.

Use a staged border instead. Start with a small measured area around spawn, for example a 2,000–5,000 block radius, monitor disk use and generation rate, and expand only after testing. A 5,000-block radius circle is roughly 307,000 chunks, which is still substantial but is approximately 12,700 times smaller than the proposed square.

Example controlled sequence:

```text
chunky world minecraft:overworld
chunky shape circle
chunky spawn
chunky radius 2000
chunky start
```

Pause generation during active peak hours and take a backup before expanding the border. Do not run simultaneous generation tasks on an 8 GiB production instance until tick time and disk growth have been measured.

## Recommended next implementation order

1. Configure Java 25 and obtain a clean Gradle build.
2. Pin and test Lithium, FerriteCore, Krypton, and an exact Spark build on a copy of the server.
3. Add the client companion initializer and the versioned handshake payload.
4. Add server-to-client HUD snapshots for mana, thirst, class, level, skill bindings, cooldowns, and quality.
5. Build and publish the signed client pack and the HTTPS resource pack.
6. Create/import the two dedicated worlds and test bounds, teleport, claims, and inventory rules.
7. Start Chunky with a small radius and record CPU, tick time, disk, and chunk rate.
8. Only then deploy the production VM and expose the Fern Control Agent.
