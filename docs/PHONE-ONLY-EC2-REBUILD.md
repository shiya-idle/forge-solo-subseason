# Fern phone-only EC2 rebuild guide

This guide assumes you are using only the AWS mobile/browser console and the EC2 **Instance Connect** browser terminal. You do not need a computer, SCP, or CloudShell.

## 1. Launch or recreate the EC2 instance

Use an Ubuntu Server LTS AMI. Select the instance size you intend to use, then configure storage according to your AWS budget. For the current Fern target, 8 GiB RAM is workable with a 5–6 GiB Java heap.

Create or select a PEM key and save it securely. You may still need it for recovery, even though Instance Connect can open a browser terminal.

## 2. Configure the AWS security group

Add only these inbound rules:

```text
SSH              TCP 22       My IP only
Minecraft Java   TCP 25565    0.0.0.0/0
Bedrock Geyser   UDP 19132    0.0.0.0/0
```

Do not add these public rules:

```text
TCP 25575  RCON
TCP 8787   Fern Control Agent
```

RCON is disabled in the baseline configuration. The Control Agent listens on localhost by default.

## 3. Open the phone terminal

In the AWS mobile browser, open **EC2 → Instances → your instance → Connect → EC2 Instance Connect → Connect**. You should see a prompt similar to:

```text
ubuntu@ip-172-31-47-224:~$
```

All commands below are entered there.

## 4. Install basic packages

```bash
sudo apt update && sudo apt install -y unzip curl tmux
```

## 5. Install Java 25

Try the Ubuntu package first:

```bash
sudo apt install -y openjdk-25-jre-headless && java -version
```

If Ubuntu cannot find that package, install Temurin Java 25:

```bash
sudo mkdir -p /opt/java25 && curl -L --fail 'https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jre/hotspot/normal/eclipse' -o /tmp/java25.tar.gz && sudo tar -xzf /tmp/java25.tar.gz -C /opt/java25 --strip-components=1 && echo 'export JAVA_HOME=/opt/java25' >> ~/.bashrc && echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc && export JAVA_HOME=/opt/java25 && export PATH=$JAVA_HOME/bin:$PATH && java -version
```

The result must show Java 25 or newer.

## 6. Download the Fern server files directly from GitHub

This command downloads the repository without using a computer or CloudShell:

```bash
curl -L --fail -o /tmp/fern-repo.zip 'https://github.com/shiya-idle/forge-solo-subseason/archive/refs/heads/main.zip' && rm -rf /tmp/fern-repo && mkdir -p /tmp/fern-repo && unzip -q /tmp/fern-repo.zip -d /tmp/fern-repo && mkdir -p ~/fern-server && cp -r /tmp/fern-repo/forge-solo-subseason-main/server-bundle-20261030/. ~/fern-server/
```

The repository is private. If GitHub returns a download or authorization error, download the ZIP from the GitHub page while logged in, or use the direct temporary bundle URL provided by Manus. Do not put a GitHub token into a command that may be saved in shell history.

## 7. Verify the bundle

```bash
cd ~/fern-server && sha256sum -c SHA256SUMS.txt
```

The files should report `OK`.

## 8. Prepare the first server start

Disable required resource-pack enforcement for the first test because the pack has not yet been hosted on your HTTPS domain:

```bash
cd ~/fern-server && cp config/server.properties.example server.properties && sed -i 's/require-resource-pack=true/require-resource-pack=false/' server.properties
```

Start the server:

```bash
cd ~/fern-server && java -Xms5G -Xmx6G -jar fabric-server-launch.jar nogui
```

The first start may download Minecraft files and create the EULA. Stop it with `Ctrl+C` after the files are created.

Accept the EULA:

```bash
cd ~/fern-server && sed -i 's/eula=false/eula=true/' eula.txt
```

## 9. Start Fern in the background

```bash
cd ~/fern-server && tmux new -d -s fern 'java -Xms5G -Xmx6G -jar fabric-server-launch.jar nogui'
```

View recent console output:

```bash
tmux capture-pane -pt fern | tail -40
```

Attach to the console:

```bash
tmux attach -t fern
```

Detach without stopping the server by pressing `Ctrl+B`, then `D`.

## 10. Test Java Edition

Use the EC2 instance’s public IPv4 address as the Minecraft Java server address:

```text
YOUR_EC2_PUBLIC_IP:25565
```

Give yourself operator access in the server console:

```text
op YOUR_MINECRAFT_USERNAME
```

Test the Fern commands and the main survival world before adding other integrations.

## 11. Add the arena worlds

Fern expects these destinations for the public waypoints:

```text
forge_pvp_arena
forge_class_arena
forge_combiroll_arena
```

Create or import those worlds before using:

```text
/waypoints
/waypoints pvp
/waypoints class
/waypoints forge
```

The mod provides 70 PvP waypoints, 65 class-change waypoints, and 65 Combiroll forging waypoints.

## 12. Configure Bedrock later

Install compatible Geyser and Floodgate builds for Minecraft 26.3. Allow UDP `19132` in both the AWS security group and Ubuntu firewall.

Bedrock players then connect to:

```text
YOUR_EC2_PUBLIC_IP
Port: 19132
```

Use the included Bedrock pack:

```text
~/fern-server/resource-packs/forge-solo-bedrock-26.3.mcpack
```

## 13. Configure the Java resource pack

Upload this file to ReadySpace or another HTTPS file host:

```text
~/fern-server/resource-packs/forge-solo-visuals-26.3.zip
```

Edit the server properties:

```bash
nano ~/fern-server/server.properties
```

Set:

```properties
resource-pack=https://YOUR-DOMAIN.example/forge-solo-visuals-26.3.zip
resource-pack-sha1=1aae096cb8982d33566efcde1595484c2bd7bae3
require-resource-pack=true
```

Restart the server only after the URL works in a browser.

## 14. Install the Fern Control Agent

Do this only after Minecraft is working. The agent files are here:

```text
~/fern-server/deploy/fern-control-agent
```

Read its guide:

```bash
sed -n '1,220p' ~/fern-server/deploy/fern-control-agent/README.md
```

The agent listens on `127.0.0.1:8787` by default. Keep it there. Do not add port `8787` to AWS or UFW.

Generate a different secret for this EC2 instance. Keep `/etc/fern-control-agent.env` private. Run the Minecraft service as a non-root `minecraft` user and the agent as its own restricted service user.

## 15. Connect ReadySpace Fern

The intended architecture is:

```text
ReadySpace Fern PHP bridge → authenticated private HTTPS/tunnel → Fern Control Agent → systemd → Fabric Minecraft
```

The current PHP bridge is not yet wired to the Control Agent. The next integration work is to add HMAC-SHA256 request signing to the private PHP configuration and migrate these routes one by one:

```text
/v1/status
/v1/power
/v1/command
/v1/logs
```

Until that integration is complete, the Fern website is only the panel UI. Do not expose the agent publicly and do not place its HMAC secret in `public_html` or JavaScript.

## 16. Recovery after a future instance rebuild

Repeat steps 1–9. The code and server bundle are preserved in the private GitHub repository:

```text
https://github.com/shiya-idle/forge-solo-subseason
```

The website repository is:

```text
https://github.com/shiya-idle/craft-control-panel
```

Keep these outside GitHub:

- PEM keys
- AWS credentials
- Filled `fern-config.php`
- HMAC secrets
- SMTP credentials
- RCON passwords
- World folders
- Player data
- Logs and backups
