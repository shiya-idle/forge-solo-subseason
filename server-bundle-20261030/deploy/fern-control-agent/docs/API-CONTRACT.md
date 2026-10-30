# Fern Control Agent API contract v0.1

Base URL is private and should normally be `http://127.0.0.1:8787` behind a local relay. All `/v1/*` requests require HMAC headers:

- `X-Fern-Timestamp`: current Unix timestamp in seconds.
- `X-Fern-Nonce`: unique random value, 16–96 URL-safe characters.
- `X-Fern-Signature`: lowercase hex HMAC-SHA256 of `timestamp.nonce.method.path.rawBody`.

The body is the exact UTF-8 string sent over HTTP. For GET requests the body is empty. The agent rejects old timestamps and reused nonces.

## Health

`GET /healthz` is a local process health check and does not expose server state. It returns `{ "ok": true, "service": "fern-control-agent" }`.

## Status

`GET /v1/status` returns the systemd state, configured public address, and agent timestamp:

```json
{ "ok": true, "service": "fern-minecraft.service", "current_state": "active", "address": "play.example.com:25565", "agent_time": "2026-10-29T00:00:00.000Z" }
```

## Power

`POST /v1/power` accepts only `start`, `stop`, or `restart` and maps them to the configured Minecraft systemd service:

```json
{ "signal": "restart" }
```

## Command

`POST /v1/command` accepts a deliberately small allowlist: `list`, `say`, `save-all`, `save-off`, `save-on`, `time`, `weather`, `difficulty`, `gamerule`, `whitelist`, and `fwhitelist`. Commands are sent through the configured RCON client, never through a shell.

```json
{ "command": "say Fern maintenance begins in 5 minutes" }
```

## Logs

`GET /v1/logs?lines=100` returns bounded systemd journal output for the Minecraft service. The maximum is 500 lines.

## Deliberately not included yet

The first contract does not include arbitrary file read/write, uploads, backups, restore, server installation, allowlist database mutation, or shell execution. Those features require separate path restrictions, size limits, transactional writes, audit events, and explicit rollback rules before they should be connected to the website.
