# Fern Minecraft ecosystem

Fern is a Minecraft 26.3 Fabric server project with a Java client companion, Bedrock/Geyser compatibility, server-authoritative skills, claims, thirst, vendors, Combirolls, public arena waypoints, custom HUD payloads, and the Fern website/control-plane components.

This repository is the source of truth for the Minecraft side. The separate website repository is `https://github.com/shiya-idle/craft-control-panel`.

## Current verified target

- Minecraft: 26.3
- Java: 25 or newer
- Fabric Loader: 0.19.5
- Fabric server launcher: 1.0.3
- Fabric API: 0.165.0+26.3
- Recommended heap on an 8 GiB VM: `-Xms5G -Xmx6G`
- Production platform: Fabric

The latest server-ready files are in [`server-bundle-20261030`](server-bundle-20261030). It contains the Fabric launcher, exact Fabric API JAR, Fern mod JAR, configs, Java resource pack, Bedrock pack, Control Agent files, and deployment notes.

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

Fern does not silently create or reset dedicated worlds. Create or import these before using their public waypoints:

```text
forge_pvp_arena
forge_class_arena
forge_combiroll_arena
```

Players can use `/waypoints`, `/waypoints pvp`, `/waypoints class`, `/waypoints forge`, `/waypoint pvp-001`, `/waypoint class-001`, and `/waypoint forge-001`. There are 70 PvP, 65 class-change, and 65 Combiroll forging waypoints.

Combiroll commands include `/forge combiroll list`, `/forge combiroll craft <materialA> <materialB>`, and `/forge combiroll cast <materialA> <materialB>`.

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

The Fabric artifact is `platform-fabric/build/libs/platform-fabric-0.1.0-SNAPSHOT.jar`. The final verified JAR is copied into the bundle as `mods/fern-forge-solo-subseason-0.1.0-mc26.3.jar`.

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
