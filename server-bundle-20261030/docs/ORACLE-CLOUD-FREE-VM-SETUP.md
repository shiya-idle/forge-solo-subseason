# Oracle Cloud Always Free VM Setup

This guide prepares an Oracle Cloud Infrastructure VM for the Forge server, Fern host files, Discord bot, and resource-pack delivery. It does not create the Oracle account, VM, firewall rules, or DNS records for you.

Oracle’s current documentation lists Always Free resources in the tenancy’s home region. The Ampere A1 allowance is described as 2 OCPUs and 12 GB RAM total, with a 200 GB combined Always Free block-volume allowance. Availability is not guaranteed; an `out of host capacity` message means the selected region or availability domain temporarily has no capacity. Oracle can also reclaim an idle Always Free compute instance under its published idle criteria, so monitor the machine after deployment. [1]

The recommended layout for this project is one Ubuntu Arm64 Ampere A1 VM with 2 OCPUs and 12 GB RAM. Keep the Minecraft server, Fern web files, Discord bot, resource-pack files, backups, and logs on the same VM initially. If the server later needs more isolation, move Fern and the bot to separate services without changing the Minecraft data directory.

## 1. Prepare an SSH key

Create the key on your own computer, not inside the VM:

```bash
ssh-keygen -t ed25519 -a 100 -f ~/.ssh/forge-oracle -C "forge-oracle"
chmod 600 ~/.ssh/forge-oracle
```

Keep `~/.ssh/forge-oracle` private. Upload only `~/.ssh/forge-oracle.pub` to OCI. Oracle uses key-based SSH authentication for Linux instances and does not keep a copy of a generated private key, so losing the private key can prevent access to the instance. [2]

## 2. Create the OCI network

1. Sign in to the Oracle Cloud Console.
2. Select the tenancy’s **home region**.
3. Open **Networking → Virtual cloud networks**.
4. Use **Start VCN Wizard** and choose **Create VCN with Internet Connectivity**.
5. Use a private CIDR such as `10.0.0.0/16` for the VCN and `10.0.0.0/24` for the public subnet.
6. Confirm that the subnet has an Internet Gateway and a route rule for `0.0.0.0/0`.
7. Prefer a Network Security Group for the instance. If the wizard creates only a security list, edit that list carefully because its rules apply to every VNIC in the subnet. Oracle recommends understanding the difference between security lists and NSGs before adding broad ingress rules. [3]

Add only these initial ingress rules:

- TCP `22` from your own public IP, for SSH.
- TCP `25565` from `0.0.0.0/0`, for Java Minecraft, if the server is public.
- UDP `19132` from `0.0.0.0/0`, for Bedrock/Floodgate, if Bedrock is enabled.
- TCP `80` from `0.0.0.0/0`, temporarily for HTTP certificate setup.
- TCP `443` from `0.0.0.0/0`, for Fern and resource-pack HTTPS delivery.

Do not expose Pterodactyl, RCON, SMTP, MySQL, Redis, or the Fern control API directly to the Internet. Bind internal services to `127.0.0.1` or a private VCN address and place Fern behind HTTPS.

## 3. Create the VM

1. Open **Compute → Instances → Create instance**.
2. Name it `forge-prod-01`.
3. Choose the home region and an available availability domain.
4. Select **Ubuntu** and choose an **Always Free Eligible** Arm64 image.
5. Select `VM.Standard.A1.Flex`.
6. Allocate up to the Always Free allowance: start with 2 OCPUs and 12 GB RAM if the console accepts it. If the game server is the priority, use 2 OCPUs and 10–12 GB RAM and leave enough storage for backups.
7. Choose a public subnet and assign a public IPv4 address.
8. Set the boot volume to 100–150 GB initially. Keep total boot and block-volume use inside the current Always Free allowance.
9. Upload `~/.ssh/forge-oracle.pub` under SSH keys.
10. Review and create the instance.

Oracle documents the instance workflow as basic information, security, networking, storage, and review. A public IP is needed for direct Internet SSH; a private-only instance requires a bastion or another private access path. [4]

If OCI reports insufficient capacity, try another availability domain in the home region or try again later. Do not create a paid shape by accident while trying to bypass an Always Free capacity error.

## 4. Connect and perform first boot hardening

Find the public IP in the instance details, then connect with the Ubuntu username:

```bash
ssh -i ~/.ssh/forge-oracle ubuntu@VM_PUBLIC_IP
```

Run:

```bash
sudo apt update
sudo apt full-upgrade -y
sudo apt install -y ca-certificates curl unzip zip git ufw fail2ban nginx certbot python3-certbot-nginx jq htop tmux rsync
sudo timedatectl set-timezone Asia/Manila
```

Enable the host firewall. Replace `YOUR_PUBLIC_IP/32` with your own current public IP:

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from YOUR_PUBLIC_IP/32 to any port 22 proto tcp
sudo ufw allow 25565/tcp
sudo ufw allow 19132/udp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

Keep the OCI NSG/security list and UFW rules aligned. A connection must pass both layers. If you lock yourself out of SSH, use the OCI Console connection or recovery workflow before changing more rules.

Create a deploy user instead of running game services as `root`:

```bash
sudo adduser --disabled-password --gecos "" forge
sudo usermod -aG sudo forge
sudo install -d -m 700 -o forge -g forge /home/forge/.ssh
sudo cp /home/ubuntu/.ssh/authorized_keys /home/forge/.ssh/authorized_keys
sudo chown forge:forge /home/forge/.ssh/authorized_keys
sudo chmod 600 /home/forge/.ssh/authorized_keys
```

After testing a second SSH session as `forge`, disable password authentication and root SSH access:

```bash
sudoedit /etc/ssh/sshd_config.d/10-forge-hardening.conf
```

Use:

```text
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
```

Then validate and reload:

```bash
sudo sshd -t
sudo systemctl reload ssh
```

## 5. Install Java and project dependencies

The current Fabric project targets Java 25. Install the ARM64 Java 25 package available in the chosen Ubuntu release, or install a trusted ARM64 Temurin/OpenJDK 25 distribution. Verify it before deploying:

```bash
java -version
uname -m
```

The expected architecture is `aarch64` or `arm64`. Do not copy an x86-only binary to this VM.

For the Discord bot:

```bash
sudo apt install -y nodejs npm
node --version
npm --version
```

Use the Node version supported by the bot’s package lock. Keep bot secrets in an environment file readable only by `forge`:

```bash
chmod 600 /home/forge/fern-discord-bot/.env
```

## 6. Deploy the Forge server

Create a stable directory layout:

```bash
sudo install -d -o forge -g forge /srv/forge/server
sudo install -d -o forge -g forge /srv/forge/backups
sudo install -d -o forge -g forge /srv/fern/public
sudo install -d -o forge -g forge /srv/fern/resource-pack
```

Copy the Fabric server, server properties, world, configuration, and mod JAR into `/srv/forge/server`. Do not copy secrets into Git. The first start should be done inside `tmux` so you can see startup errors:

```bash
sudo -iu forge
cd /srv/forge/server
tmux new -s forge
java -Xms4G -Xmx8G -jar fabric-server-launch.jar nogui
```

Accept the EULA only after reviewing the Minecraft server license. Stop the server cleanly, then create a systemd unit:

```bash
sudo tee /etc/systemd/system/forge-server.service >/dev/null <<'UNIT'
[Unit]
Description=Forge Solo Subseason Minecraft server
After=network-online.target
Wants=network-online.target

[Service]
User=forge
Group=forge
WorkingDirectory=/srv/forge/server
ExecStart=/usr/bin/java -Xms4G -Xmx8G -jar fabric-server-launch.jar nogui
Restart=on-failure
RestartSec=10
TimeoutStopSec=60
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now forge-server
sudo journalctl -u forge-server -f
```

Set the Java heap below the VM’s total memory. Leave headroom for the OS, Fern, the bot, Nginx, and file caching. Adjust `-Xmx` only after observing memory use.

## 7. Deploy Fern and the resource pack

Copy the Fern public files into `/srv/fern/public`. Copy the generated pack into `/srv/fern/resource-pack/forge-solo-visuals-26.3.zip`.

Create an Nginx site:

```bash
sudo tee /etc/nginx/sites-available/fern >/dev/null <<'NGINX'
server {
    listen 80;
    server_name fern.example.com;
    root /srv/fern/public;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location = /forge-solo-visuals-26.3.zip {
        alias /srv/fern/resource-pack/forge-solo-visuals-26.3.zip;
        default_type application/zip;
        add_header Cache-Control "public, max-age=3600";
    }
}
NGINX
sudo ln -s /etc/nginx/sites-available/fern /etc/nginx/sites-enabled/fern
sudo nginx -t
sudo systemctl reload nginx
```

Point the DNS A record for `fern.example.com` to the VM public IP. Then request HTTPS:

```bash
sudo certbot --nginx -d fern.example.com
```

Use the HTTPS pack URL in `server.properties` and retain the exact SHA-1 from the pack deployment snippet:

```text
resource-pack=https://fern.example.com/forge-solo-visuals-26.3.zip
resource-pack-sha1=87a169720202837bdf861f228c0f856b9e35bab9
require-resource-pack=true
```

After replacing the pack, calculate a new checksum and update `server.properties` before restarting the Minecraft server.

## 8. Deploy the Discord bot

```bash
sudo -iu forge
cd /srv
sudo -u forge git clone YOUR_BOT_REPOSITORY fern-discord-bot
cd /srv/fern-discord-bot
npm ci
npm run build
```

Create `/srv/fern-discord-bot/.env` with the Discord token, guild IDs, SMTP values, and any API keys. Never place those values in the repository or the website public directory.

Create a systemd unit:

```bash
sudo tee /etc/systemd/system/fern-discord-bot.service >/dev/null <<'UNIT'
[Unit]
Description=Fern Discord bot
After=network-online.target forge-server.service
Wants=network-online.target

[Service]
User=forge
Group=forge
WorkingDirectory=/srv/fern-discord-bot
EnvironmentFile=/srv/fern-discord-bot/.env
ExecStart=/usr/bin/node dist/index.js
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now fern-discord-bot
sudo journalctl -u fern-discord-bot -f
```

## 9. Backups and maintenance

Back up the world, configs, claims, bot data, and Fern data. Do not rely only on the boot volume snapshot. A basic local rotation is:

```bash
sudo install -d -o forge -g forge /srv/forge/backups
sudo tee /usr/local/sbin/forge-backup >/dev/null <<'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail
stamp=$(date -u +%Y%m%d-%H%M%S)
out=/srv/forge/backups/forge-$stamp.tar.zst
nice -n 10 tar --zstd -cf "$out" \
  /srv/forge/server/world \
  /srv/forge/server/config \
  /srv/forge/server/mods \
  /srv/fern-discord-bot/data \
  /srv/fern/public
find /srv/forge/backups -type f -name 'forge-*.tar.zst' -mtime +14 -delete
SCRIPT
sudo chmod 750 /usr/local/sbin/forge-backup
sudo tee /etc/systemd/system/forge-backup.service >/dev/null <<'UNIT'
[Unit]
Description=Forge server backup

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/forge-backup
UNIT
sudo tee /etc/systemd/system/forge-backup.timer >/dev/null <<'UNIT'
[Unit]
Description=Nightly Forge backup

[Timer]
OnCalendar=*-*-* 04:00:00 UTC
Persistent=true

[Install]
WantedBy=timers.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now forge-backup.timer
```

Copy important backups off the VM. If the VM is deleted, local backups disappear with it.

## 10. Final verification checklist

Run these checks after deployment:

```bash
systemctl --failed
systemctl status forge-server fern-discord-bot nginx
ss -lntup
ufw status verbose
curl -I https://fern.example.com/
curl -I https://fern.example.com/forge-solo-visuals-26.3.zip
sha1sum /srv/fern/resource-pack/forge-solo-visuals-26.3.zip
```

Test Minecraft Java on TCP `25565`, Bedrock on UDP `19132` if enabled, Fern over HTTPS, Discord bot restart behavior, resource-pack acceptance, claims persistence, and the 21-day Nightmare activation clock.

## References

[1]: https://docs.oracle.com/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm "Oracle Cloud Always Free Resources"
[2]: https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/managingkeypairs.htm "Oracle Cloud Managing Key Pairs on Linux Instances"
[3]: https://docs.oracle.com/iaas/Content/Network/Concepts/securitylists.htm "Oracle Cloud Security Lists"
[4]: https://docs.oracle.com/iaas/Content/Compute/Tasks/launchinginstance.htm "Oracle Cloud Creating an Instance"
