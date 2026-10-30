# Fern client companion setup

## Java Edition players

The server now requires the Fern client companion for Java Edition players. It does not silently install a jar into a player's launcher. Publish a client pack containing:

- Fabric Loader for Minecraft 26.3
- Fabric API `0.165.0+26.3`
- `forge-solo-subseason` client jar built from this project
- the exact client-only dependencies selected for the final pack

The companion reports protocol version `1` and build `fern-client-1`. The server sends a challenge after join. The client echoes the challenge through the `forge_solo:companion_hello` payload. Clients that do not answer are disconnected after the verification timeout.

The server also sends HUD snapshots through `forge_solo:hud_snapshot`. The snapshot contains health from the normal game HUD plus server-authoritative mana, thirst, class, quality, and bound skill slots. The client must never calculate gameplay values or accept client values as authoritative.

## Bedrock players

Bedrock Edition cannot load a Java Fabric client mod. Bedrock players therefore use the compatibility path:

1. Install Geyser-Fabric and Floodgate on the server.
2. Keep Floodgate authentication enabled and verify that Bedrock players are recognized by the Floodgate API.
3. The Fern server detects Floodgate players without a hard compile-time dependency.
4. Bedrock players bypass the Java companion handshake and receive a server-side action-bar HUD showing health, mana, and thirst.
5. The required Java resource-pack rule must be tested with the selected Geyser version. If the pack is not usable by Bedrock, keep critical information in the action bar and do not make Bedrock access depend on client-only overlays.

The Bedrock path is not a Java client mod. It is a server-side compatibility version of the HUD and gameplay systems. Skills, claims, thirst, vendor effects, and PvP rules remain server-authoritative and work for both Java and Bedrock players.

## Production checks

Before opening the server:

1. Build with Java 25.
2. Confirm the client jar contains both `main` and `client` entrypoints.
3. Test a Java client with the companion: it should join and show `Fern client companion verified.`
4. Test a vanilla Java client: it should be disconnected with the installation message.
5. Test a Floodgate Bedrock player: it should bypass the Java handshake and receive the action-bar fallback.
6. Test a Java player whose companion has the wrong protocol: it should be rejected.
7. Upload the resource pack to HTTPS and set the real URL and SHA-1 in `server.properties`.
