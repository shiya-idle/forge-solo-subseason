# Fern Control Agent

The Fern Control Agent is the private AWS-side boundary between the ReadySpace/cPanel website and the Minecraft server. It listens on `127.0.0.1` by default, so it is not directly exposed to the internet. A reverse proxy or private tunnel can be added later; the cPanel bridge must never connect to an unauthenticated public port.

## Current scope

Version 0.1 provides authenticated status, power controls, safe RCON commands, and systemd journal logs. It intentionally does not yet expose arbitrary shell commands, arbitrary file paths, player database writes, or restore/delete operations. Those should be added only through separate allowlisted handlers.

The request authentication scheme is HMAC-SHA256. The client signs `timestamp.nonce.method.path.rawBody` with the shared secret and sends `X-Fern-Timestamp`, `X-Fern-Nonce`, and `X-Fern-Signature`. Requests older than the configured clock window and reused nonces are rejected.

## AWS installation

Run these commands on the Ubuntu EC2 instance after the Minecraft service has been created. Replace the placeholder secret before starting the service.

```bash
sudo useradd --system --home /opt/fern-control-agent --shell /usr/sbin/nologin fern && sudo mkdir -p /opt/fern-control-agent && sudo chown -R fern:fern /opt/fern-control-agent
```

Copy the `src`, `package.json`, and `systemd/fern-control-agent.service` files into `/opt/fern-control-agent`, then create the private environment file:

```bash
sudo install -o root -g fern -m 0640 .env.example /etc/fern-control-agent.env && sudo sed -i "s#replace-with-a-64-character-random-secret#$(openssl rand -hex 32)#" /etc/fern-control-agent.env
```

Set the real service name, RCON binary path, and public server address in `/etc/fern-control-agent.env`. Then install and start the unit:

```bash
sudo install -o root -g root -m 0644 systemd/fern-control-agent.service /etc/systemd/system/fern-control-agent.service && sudo systemctl daemon-reload && sudo systemctl enable --now fern-control-agent.service
```

Check the local health endpoint and service status:

```bash
curl -fsS http://127.0.0.1:8787/healthz && sudo systemctl --no-pager --full status fern-control-agent.service
```

The service should be reachable only on localhost. Do not open TCP 8787 in the AWS security group. If the website will later reach the agent over a tunnel or reverse proxy, expose only HTTPS, restrict the source, and retain HMAC authentication.

## Minecraft service requirements

Create a dedicated `fern-minecraft.service` unit for the Fabric server and run it as a separate `minecraft` user. Do not run the game as `root`. Configure RCON on localhost only if the command endpoint is enabled, and set `FERN_RCON_BIN` to a dedicated RCON client whose credentials are not exposed to the website.

The agent's systemd user `fern` needs only the permissions required to invoke `systemctl start/stop/restart fern-minecraft.service`, read that service's journal, and execute the configured RCON client. Before production use, add a narrowly scoped sudoers rule or a root-owned helper rather than granting broad sudo access.

## Security checklist

Keep `/etc/fern-control-agent.env` mode `0640` and group-readable only by the agent group. Generate a new secret for every environment. Keep the clock synchronized with `systemd-timesyncd` or chrony. Do not reuse the HMAC secret as an RCON password. Do not add a catch-all shell endpoint. Log authentication failures without logging the secret, raw Authorization headers, or player credentials.

The ReadySpace PHP bridge is not connected to this agent yet. The next integration step is to add the same signing algorithm to the private PHP configuration and replace individual panel calls with `/v1/status`, `/v1/power`, `/v1/command`, and `/v1/logs` one action at a time.
