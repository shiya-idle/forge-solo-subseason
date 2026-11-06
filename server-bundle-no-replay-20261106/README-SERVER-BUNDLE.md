# Fern Fabric Server Bundle

This is the verified Fern add-on bundle for Minecraft 26.3. The Fabric mod JAR was built successfully with Java 25. It includes the Fern skills, mana, thirst, claims, vendor, Combirolls, public waypoints, Java client companion, Bedrock fallback, and one-million-block border policy.

This archive includes the Fabric server launcher and the exact Fabric API JAR used by the verified build. It does not include third-party Chunky/Geyser/Floodgate JARs, which must be added separately if those features are needed.

## Install

1. Install Java 25 or newer on the server.
2. Use the included `fabric-server-launch.jar`; it is configured for Minecraft 26.3 and Fabric Loader 0.19.5.
3. Place the included `mods/fabric-api-0.165.0+26.3.jar` and `mods/fern-forge-solo-subseason-0.1.0-mc26.3.jar` in the server `mods/` directory.
5. Install a Minecraft 26.3-compatible Fabric build of Chunky if you want controlled pregeneration.
6. For Bedrock access, install matching Geyser and Floodgate versions and follow the Bedrock pack guide.
7. Copy the files in `config/` into the server `config/` directory. Review them before first launch.
8. Copy `config/server.properties.example` to the server root as `server.properties`, then set the server address, ports, online-mode, whitelist, and operator list for your environment.
9. Upload `resource-packs/forge-solo-visuals-26.3.zip` to an HTTPS URL. Replace `YOUR-HOST.example` in `config/forge-solo-visuals-server.properties` with that URL. The SHA-1 in that file matches the included pack.
10. Keep `require-resource-pack=true` if the Java HUD and visual profiles are mandatory.
11. Create or import the configured dimensions before using their waypoints: `forge_pvp_arena`, `forge_class_arena`, and `forge_combiroll_arena`. Fern will not silently generate or reset these worlds.
12. Start the Fabric server with at least 5–6 GiB heap on an 8 GiB machine, for example: `java -Xms5G -Xmx6G -jar fabric-server-launch.jar nogui`.

## Important commands

```text
/waypoints
/waypoints pvp
/waypoints class
/waypoints forge
/waypoint pvp-001
/waypoint class-001
/waypoint forge-001
/forge combiroll list
/forge combiroll craft copper iron
/forge combiroll cast copper iron
```

An operator can spawn the Combiroll Altar with `/forge combiroll spawn`. The 200 public waypoint entries are divided into 70 PvP, 65 class-change, and 65 forging waypoints.

## Included packs

The Java pack is the server resource pack. The Bedrock `.mcpack` is the Geyser/Bedrock visual pack and should be configured through the Geyser pack workflow described in `docs/BEDROCK-PACK-AND-BORDER-SETUP.md`.

The Fern Control Agent in `deploy/fern-control-agent/` is optional. It is a separate restricted service for future control-panel integration and should not be exposed publicly until its HMAC secret and firewall rules are configured.

## Build verification

The final build command was:

```text
JAVA_HOME=/path/to/java-25 ./gradlew clean build --no-daemon
```

The resulting Fabric build completed successfully. The server mod JAR is the file in `mods/`, not the `-sources.jar` artifact.
