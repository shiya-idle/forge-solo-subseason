# Forge Solo Subseason — Fabric platform

Target: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.165.0+26.3, Java 25.

This module is the real-server target. The older `platform-paper` module is retained only for historical/testing compatibility and must not be installed on the Fabric server.

## UI contract

The server requires a resource pack. The first UI slice uses a vanilla-compatible two-row skill panel with 18 server-owned slots, only showing assigned skills. Slot contents, mana indicator visibility, cooldown display, and effect settings are server-side state; the client cannot grant itself a skill or bypass mana/cooldowns.

A resource pack can reskin the panel, icons, slot backgrounds, fonts, and sounds. It cannot independently create a persistent clickable HUD, so the safe implementation uses a server-opened inventory panel plus an action-bar mana indicator. A future client companion may replace this with a true right-side HUD without changing the server-authoritative skill protocol.

## Visual effects

The initial signature skills now use bounded layered particle compositions instead of a single generic burst. Each cast can combine a primary particle, an accent particle, and a ground ring, with themed families for nature, arcane, radiant, shadow, and impact skills. The YAML controls density, ring radius, ring points, layered particles, and combat highlights. The hard particle cap remains active so visual quality cannot turn into an unbounded performance cost. A resource pack can add matching icon, sound, and font styling through the named visual profiles without granting the client authority over effects.

## Persistent immutable claims

Claims are stored atomically in `config/forge-claims.yml` and loaded before block-event handlers are enabled. If that file cannot be read or written, claim creation is disabled rather than risking a claim that disappears after restart. Normal players can stage a request with `/claims request <minX> <minY> <minZ> <maxX> <maxY> <maxZ>`, then confirm within five minutes using `/claims confirm <server-code> CLAIM_FOREVER I_UNDERSTAND`. The server code, exact intent phrase, and explicit acknowledgement are three separate confirmations. Claims cannot be deleted or resized by normal players, and overlapping claims are rejected.

Protected building definitions are loaded from `config/forge-buildings.yml`. Add the final building codes and coordinates there before enabling the corresponding purge rules in production.

## Required server settings

Configure the pack URL and SHA-1 in `server.properties`, set `require-resource-pack=true`, and test the pack with both Java and Bedrock clients before production. Do not embed private URLs or credentials in this repository.
