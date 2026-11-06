# Fern AWS VM setup — phone-only, no cPanel, no Fern panel

This guide runs Fern directly on an AWS EC2 Ubuntu VM. It does not require ReadySpace cPanel, the Fern web panel, Pterodactyl, CloudShell, SCP, or a computer.

The target is:

- Minecraft 26.3
- Fabric Loader 0.19.5
- Java 25
- 8 GiB RAM recommended
- 100 GB gp3 storage recommended
- Java TCP port 25565
- Bedrock UDP port 19132 when Geyser/Floodgate are installed
- Fern Fabric mod with no Replay Mod, Flashback, or recorder component

## 1. Create the EC2 instance from a phone

Open the AWS mobile website and choose **EC2 → Instances → Launch instance**.

Use:

- Name: `fern-fabric-263`
- AMI: Ubuntu Server LTS 64-bit
- Instance type: an 8 GiB RAM instance available in your region
- Storage: 100 GiB gp3
- Key pair: create/download a PEM key and store it safely
- Auto-assign public IPv4: enabled

Create or select a security group with only these inbound rules:

```text
SSH              TCP 22       My IP only
Minecraft Java   TCP 25565    0.0.0.0/0
Bedrock Geyser   UDP 19132    0.0.0.0/0
```

Do not add these:

```text
RCON             TCP 25575
Fern Agent       TCP 8787
Pterodactyl      Any port
```

## 2. Connect using the phone browser

In AWS:

1. Open **EC2 → Instances**.
2. Select `fern-fabric-263`.
3. Tap **Connect**.
4. Choose **EC2 Instance Connect**.
5. Choose **Connect using the browser-based SSH connection**.

All commands below are single-line commands suitable for the mobile terminal.

## 3. Install required packages

```bash
sudo apt update && sudo apt install -y unzip curl tmux tar ca-certificates
```

## 4. Install Java 25

Try the Ubuntu package first:

```bash
sudo apt install -y openjdk-25-jre-headless && java -version
```

If Ubuntu cannot find it, install Temurin Java 25:

```bash
sudo mkdir -p /opt/java25 && curl -L --fail 'https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jre/hotspot/normal/eclipse' -o /tmp/java25.tar.gz && sudo tar -xzf /tmp/java25.tar.gz -C /opt/java25 --strip-components=1 && echo 'export JAVA_HOME=/opt/java25' >> ~/.bashrc && echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc && export JAVA_HOME=/opt/java25 && export PATH=$JAVA_HOME/bin:$PATH && java -version
```

The version must show Java 25 or newer.

## 5. Download the no-replay server bundle

Use the GitHub repository ZIP:

```bash
curl -L --fail -o /tmp/fern-repo.zip 'https://github.com/shiya-idle/forge-solo-subseason/archive/refs/heads/main.zip' && rm -rf /tmp/fern-repo && mkdir -p /tmp/fern-repo && unzip -q /tmp/fern-repo.zip -d /tmp/fern-repo && rm -rf ~/fern-server && mkdir -p ~/fern-server && cp -r /tmp/fern-repo/forge-solo-subseason-main/server-bundle-no-replay-20261106/. ~/fern-server/
```

If the repository is private, download the no-replay ZIP while signed in to GitHub and upload/extract it through the AWS browser workflow. Do not put a GitHub token into a command that may be saved in shell history.

## 6. Verify the no-replay bundle

```bash
cd ~/fern-server && sha256sum -c SHA256SUMS.txt
```

The checks should report `OK`.

The bundle intentionally contains only:

- Fabric Loader launcher
- Fabric API
- Fern gameplay Fabric mod
- Fern configuration files
- Java visual resource pack
- Bedrock resource pack

It does not contain Replay Mod, Flashback, FernReplayCapture, or any recorder plugin.

## 7. Prepare server properties

```bash
cd ~/fern-server && cp config/server.properties.example server.properties && sed -i 's/require-resource-pack=true/require-resource-pack=false/' server.properties && sed -i 's/^server-port=.*/server-port=25565/' server.properties
```

Keep `require-resource-pack=false` until the Java resource pack is hosted at a working HTTPS URL. You can enable it later.

## 8. First server start

```bash
cd ~/fern-server && java -Xms5G -Xmx6G -jar fabric-server-launch.jar nogui
```

Wait until `eula.txt` is created. Stop the process with `Ctrl+C`.

Accept the EULA:

```bash
cd ~/fern-server && sed -i 's/eula=false/eula=true/' eula.txt
```

## 9. Install the permanent Minecraft system service

This keeps the server running after the phone terminal closes or the VM reboots.

```bash
printf '%s\n' '[Unit]' 'Description=Fern Minecraft Fabric Server' 'After=network-online.target' 'Wants=network-online.target' '' '[Service]' 'Type=simple' 'User=ubuntu' 'WorkingDirectory=/home/ubuntu/fern-server' 'ExecStart=/usr/bin/env java -Xms5G -Xmx6G -jar fabric-server-launch.jar nogui' 'Restart=always' 'RestartSec=10' 'KillSignal=SIGINT' 'TimeoutStopSec=60' 'LimitNOFILE=65535' '' '[Install]' 'WantedBy=multi-user.target' | sudo tee /etc/systemd/system/fern-minecraft.service >/dev/null && sudo systemctl daemon-reload && sudo systemctl enable fern-minecraft.service && sudo systemctl start fern-minecraft.service
```

Check it:

```bash
sudo systemctl --no-pager --full status fern-minecraft.service
```

The expected state is:

```text
Active: active (running)
```

View logs:

```bash
sudo journalctl -u fern-minecraft.service -f
```

Leave the log view with `Ctrl+C`. The server continues running.

## 10. Test Java Edition

Find the instance public IPv4 address in AWS and connect with:

```text
YOUR_EC2_PUBLIC_IP:25565
```

From the server log or console, grant operator access:

```bash
sudo journalctl -u fern-minecraft.service -n 80 --no-pager
```

For commands, temporarily stop the service and use tmux only if needed, or install the optional local RCON tool later. Do not expose RCON publicly.

## 11. Optional Bedrock support

Bedrock support requires matching Minecraft 26.3-compatible Geyser and Floodgate builds. Add them only after Java works. Place the JARs in:

```text
/home/ubuntu/fern-server/mods/
```

Then restart:

```bash
sudo systemctl restart fern-minecraft.service
```

Bedrock players use the instance public IP and UDP port `19132`.

Use the included pack:

```text
/home/ubuntu/fern-server/resource-packs/forge-solo-bedrock-26.3.mcpack
```

## 12. Configure the Java resource pack later

Host this file at an HTTPS URL using any file host or a future website:

```text
/home/ubuntu/fern-server/resource-packs/forge-solo-visuals-26.3.zip
```

Then edit the properties file:

```bash
sed -i 's#^resource-pack=.*#resource-pack=https://YOUR-DOMAIN.example/forge-solo-visuals-26.3.zip#; s#^require-resource-pack=.*#require-resource-pack=true#' ~/fern-server/server.properties && sudo systemctl restart fern-minecraft.service
```

Do not enable enforcement until the URL downloads successfully in a browser.

## 13. Create or import the configured arena worlds

Fern expects:

```text
forge_pvp_arena
forge_class_arena
forge_combiroll_arena
```

Do not pre-generate the full one-million-by-one-million area. The world border is enough:

```text
/worldborder center 0 0
/worldborder set 1000000
```

Use the Fern waypoints after the corresponding worlds exist.

## 14. Daily phone management without a panel

Check status:

```bash
sudo systemctl is-active fern-minecraft.service
```

Restart:

```bash
sudo systemctl restart fern-minecraft.service
```

Stop:

```bash
sudo systemctl stop fern-minecraft.service
```

Start:

```bash
sudo systemctl start fern-minecraft.service
```

View the latest log:

```bash
sudo journalctl -u fern-minecraft.service -n 100 --no-pager
```

Follow the live log:

```bash
sudo journalctl -u fern-minecraft.service -f
```

Open the server directory:

```bash
cd ~/fern-server
```

## 15. Optional agent and panel

Do not install the Fern Control Agent or configure cPanel for now. The server is fully usable without them. Keep ports `8787` and `25575` closed.

When you later want remote browser management, install the agent as a separate phase and keep its HMAC secret outside the public website.

## 16. Recovery after a VM rebuild

Repeat the instance launch, Java installation, bundle download, EULA acceptance, and systemd service steps. Keep these private and backed up:

- PEM key
- World folders
- `server.properties`
- `eula.txt`
- Player data
- Logs
- Backups
- Any future agent secret

The no-replay bundle is safe to use as the production baseline because the production Fabric JAR contains no Replay, Flashback, or recorder classes.
