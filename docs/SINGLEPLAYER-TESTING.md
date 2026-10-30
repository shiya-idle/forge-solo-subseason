# Fern single-player testing

Single-player testing uses Minecraft's integrated server. It still requires a Fabric client profile because the Fern mod contains a client companion and a server-side integrated-world component.

## Required profile

Use exactly:

- Minecraft Java 26.3
- Fabric Loader 0.19.5 or newer compatible with 26.3
- Fabric API `0.165.0+26.3`
- The built `forge_solo_subseason` jar, including both the main and client source sets
- Java 25

Put the mod jar and Fabric API jar in the profile's `mods` folder. Do not test by putting only the resource pack in the profile. Do not use a 1.21.x, 26.2, Forge, or Bedrock profile.

## What changed

The Loom build now includes both `sourceSets.main` and `sourceSets.client` in the mod jar. The integrated server also bypasses the remote required-client handshake and marks the local player as trusted because the client and server are the same running instance. Dedicated servers still require the nonce-bound handshake for Java players.

## Test steps

1. Launch the Fabric 26.3 profile once and close it.
2. Put the built Fern jar and Fabric API in that profile's `mods` folder.
3. Start a new single-player world.
4. Confirm the console or log reports Forge Solo Subseason initialized.
5. Confirm vanilla hearts are replaced by the Fern health bar.
6. Run `/skills panel`, `/quality list`, and `/forge drink`.
7. Check that mana, thirst, class, and skill slots appear in the HUD.

If it still fails, send the complete `logs/latest.log` and any file in `crash-reports`. The first `Caused by:` section is the important part.
