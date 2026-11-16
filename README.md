# Fern Minecraft ecosystem

Fern is a Minecraft 26.3 Fabric server project with a Java client companion, Bedrock/Geyser compatibility, server-authoritative skills, claims, thirst, vendors, Combirolls, Cornucopia trading, Escher dungeon support, public waypoints, custom HUD payloads, and the Fern website/control-plane components.

This repository is the source of truth for the Minecraft side. The separate website repository is `https://github.com/shiya-idle/craft-control-panel`.

## Current verified target

- Minecraft: 26.3
- Java: 25 or newer
- Fabric Loader: 0.19.5
- Fabric server launcher: 1.0.3
- Fabric API: 0.165.0+26.3
- Recommended heap on an 8 GiB VM: `-Xms5G -Xmx6G`
- Production platform: Fabric

The base server-ready files are in [`server-bundle-20261030`](server-bundle-20261030). It contains the Fabric launcher, exact Fabric API JAR, Fern mod JAR, configs, Java resource pack, Bedrock pack, Control Agent files, and deployment notes. The updated Minecraft 26.3 Fern mod and compatible Escher datapack are published in the [MC 26.3 Escher/Fern release](https://github.com/shiya-idle/forge-solo-subseason/releases/tag/mc26.3-escher-fern-20261116).

## Phone-only EC2 rebuild

No computer, SCP, or CloudShell is required. Open **EC2 → Instance Connect → Connect using browser-based SSH** on a phone and run these commands in the Ubuntu terminal.

Install tools:

```bash
sudo apt update && sudo apt install -y unzip curl tmux
```

Install Java 25. Try Ubuntu's package first:

```bash
sudo apt install -y openjdk-25-jre-headless && java -version
```

If that package is unavailable, install Temurin Java 25:

```bash
sudo mkdir -p /opt/java25 && curl -L --fail 'https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jre/hotspot/normal/eclipse' -o /tmp/java25.tar.gz && sudo tar -xzf /tmp/java25.tar.gz -C /opt/java25 --strip-components=1 && echo 'export JAVA_HOME=/opt/java25' >> ~/.bashrc && echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc && export JAVA_HOME=/opt/java25 && export PATH=$JAVA_HOME/bin:$PATH && java -version
```

Download this repository directly from GitHub and use the committed bundle:

```bash
curl -L --fail -o /tmp/fern-repo.zip 'https://github.com/kammonusinno-ops/forge-solo-subseason/archive/refs/heads/main.zip' && rm -rf /tmp/fern-repo && mkdir -p /tmp/fern-repo && unzip -q /tmp/fern-repo.zip -d /tmp/fern-repo && mkdir -p ~/fern-server && cp -r /tmp/fern-repo/forge-solo-subseason-main/server-bundle-20261030/. ~/fern-server/
```

Verify the bundle and start it:

```bash
cd ~/fern-server && sha256sum -c SHA256SUMS.txt && cp config/server.properties.example server.properties && sed -i 's/require-resource-pack=true/require-resource-pack=false/' server.properties && java -Xms5G -Xmx6G -jar fabric-server-launch.jar nogui
```

The first run creates the EULA file. Stop with `Ctrl+C`, accept it, and start in the background:

```bash
cd ~/fern-server && sed -i 's/eula=false/eula=true/' eula.txt && tmux new -d -s fern 'java -Xms5G -Xmx6G -jar fabric-server-launch.jar nogui'
```

View the server with `tmux capture-pane -pt fern | tail -40`. Attach later with `tmux attach -t fern`; detach without stopping it with `Ctrl+B`, then `D`.

## AWS rules

Open only these inbound rules in the EC2 security group:

```text
TCP 22       Your IP only
TCP 25565    0.0.0.0/0       Java Edition
UDP 19132    0.0.0.0/0       Bedrock through Geyser
```

Do not expose TCP `25575` for RCON or TCP `8787` for the Fern Control Agent. RCON is disabled in the baseline configuration. The Control Agent listens on localhost by default.

## Resource packs

Host `server-bundle-20261030/resource-packs/forge-solo-visuals-26.3.zip` at an HTTPS URL. Then set:

```properties
resource-pack=https://YOUR-DOMAIN.example/forge-solo-visuals-26.3.zip
resource-pack-sha1=1aae096cb8982d33566efcde1595484c2bd7bae3
require-resource-pack=true
```

Do not enable `require-resource-pack=true` until the HTTPS URL works. The Bedrock pack is `forge-solo-bedrock-26.3.mcpack`; configure it through Geyser. Geyser and Floodgate are optional dependencies and must match the server version.

## Worlds and gameplay

Fern does not silently replace or reset the existing overworld. Create or import these before using their public waypoints:

```text
forge_pvp_arena
forge_class_arena
forge_combiroll_arena
forge_cornucopia
tid:layer_1 (Escher, supplied by its datapack)
```

Install the bootstrap in [`deploy-assets/20261112-cornucopia`](deploy-assets/20261112-cornucopia) to create the separate `fern:forge_cornucopia` dimension. It does not replace the existing overworld or existing `fern-arenas` datapack. A prebuilt NetherGames Hub world converted for Java is included with the [Cornucopia Hub release assets](https://github.com/shiya-idle/forge-solo-subseason/releases/tag/mc26.3-escher-fern-20261116); it is CC BY 4.0 and has an import guide in that package.

Players can use `/waypoints`, `/waypoints pvp`, `/waypoints class`, `/waypoints forge`, `/waypoints cornucopia`, `/waypoints dungeon`, `/waypoint pvp-001`, `/waypoint class-001`, `/waypoint forge-001`, `/waypoint cornucopia`, or `/waypoint dungeon`. There are 202 total access points: 70 PvP, 65 class-change, 65 Combiroll forging, one Cornucopia entrance, and one Escher dungeon entrance. `/fern return last` returns players from Escher to their saved Overworld location.

Player-held items receive owner metadata. Another player cannot pick up or use an owner-bound item outside Cornucopia, and container interactions with foreign-owned stacks are blocked; pickup in Cornucopia transfers the item to its new holder. Player death drops are owner-locked, have their lifetime reset, and report their coordinates. This is enforced by the Fabric mod, so install the rebuilt JAR before enabling the datapack.

Combiroll commands include `/forge combiroll list`, `/forge combiroll craft <materialA> <materialB>`, and `/forge combiroll cast <materialA> <materialB>`.

The Craftsman panel is `/craftsman` and offers server-validated `/craftsman craft mana_tonic` and `/craftsman craft ember_flare` recipes. The daily quest board is `/quests`; an operator can spawn its villager with `/fern questnpc spawn`. Admin elite mobs use `/fern mob spawn <vanilla-mob-id> [tier 1-5]`, `/fern mob despawn [radius]`, and `/fern mob despawn-all` (the last command scans within 128 blocks). The existing Floodgate-XUID profile for `.Yuanfreces` grants operator permission; the mutable username alone is not used for authorization.

The quest NPC currently uses a villager model. The supplied Bedrock skin pack contains Bedrock-specific skin geometry and is not directly attachable to a Java villager; a custom cross-client NPC skin model remains to be implemented. The Cornucopia Hub is CC BY 4.0; full-world PvP and Combiroll maps still need independently verified open licenses or original builds before redistribution.

## Connecting Fern on ReadySpace/cPanel

The website source is in the separate `craft-control-panel` repository. Deploy its public runtime files to `public_html` and keep the filled `fern-config.php` one directory above `public_html`. Never commit that filled config, API keys, passwords, SMTP credentials, HMAC secrets, or player runtime data.

The planned production connection is:

```text
ReadySpace Fern PHP bridge → authenticated private HTTPS/tunnel → Fern Control Agent on EC2 → systemd → Fabric Minecraft service
```

The Control Agent source is in `server-bundle-20261030/deploy/fern-control-agent`. It listens on `127.0.0.1:8787` by default and authenticates requests with HMAC-SHA256. Do not open port 8787 publicly. If ReadySpace must reach it remotely, use a private tunnel or a tightly restricted HTTPS reverse proxy, keep HMAC enabled, and restrict the source IP.

Important: the current PHP bridge is not yet wired to the Control Agent. The next integration work is to add the same signing algorithm to the private PHP configuration and replace individual panel calls with `/v1/status`, `/v1/power`, `/v1/command`, and `/v1/logs`, one action at a time. Until then, Fern is a website UI and must not be treated as a remote control for this Fabric instance.

Install the agent only after the Minecraft service works. Use `server-bundle-20261030/deploy/fern-control-agent/README.md`, generate a unique secret, keep `/etc/fern-control-agent.env` private, and run Minecraft as a separate non-root `minecraft` user.

## Building from source

```bash
export JAVA_HOME=/path/to/java-25
./gradlew clean build --no-daemon
```

The Fabric artifact is `platform-fabric/build/libs/platform-fabric-0.1.0-SNAPSHOT.jar`. The verified updated Fabric JAR and compatible Escher datapack are attached to the [MC 26.3 Escher/Fern release](https://github.com/shiya-idle/forge-solo-subseason/releases/tag/mc26.3-escher-fern-20261116).

A server cannot automatically install a Java client mod. Distribute the client companion through the approved modpack/launcher workflow. Bedrock players use Geyser/Floodgate and the server-side fallback.

## Keep private data out of GitHub

Never commit private `fern-config.php`, filled `.env` files, AWS access keys, PEM keys, HMAC secrets, SMTP passwords, RCON passwords, live player databases, world folders, logs, backups, or generated runtime state. The repository contains templates and documentation only for those items.

## New-instance recovery checklist

1. Launch Ubuntu and keep the PEM key safe.
2. Configure TCP 22, TCP 25565, and UDP 19132 only.
3. Open EC2 Instance Connect in the phone browser.
4. Install Java 25, unzip, curl, and tmux.
5. Download the repository ZIP directly from GitHub.
6. Copy `server-bundle-20261030` to `~/fern-server`.
7. Verify `SHA256SUMS.txt`.
8. Start once, accept the EULA, then start the tmux service.
9. Test Java joining before installing Geyser/Floodgate.
10. Create/import the three arena worlds.
11. Host and verify the Java resource pack over HTTPS.
12. Configure and test the Control Agent only after Minecraft is stable.
13. Connect the ReadySpace PHP bridge through the authenticated private path; never expose 8787.

## Dedicated step-by-step file

For the complete phone-only instance rebuild and Fern connection procedure, use [`docs/PHONE-ONLY-EC2-REBUILD.md`](docs/PHONE-ONLY-EC2-REBUILD.md).
