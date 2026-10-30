# Next integration sequence

1. Install the agent on the AWS EC2 instance and verify `/healthz` locally.
2. Create and test the dedicated Fabric systemd unit and RCON client under the `minecraft` user.
3. Add private `control_agent` settings to the cPanel config: base URL, HMAC secret, and connect timeout. Never place the secret in JavaScript or `public_html`.
4. Add one PHP client helper that signs the exact request body and rejects non-HTTPS public URLs unless the connection is explicitly local/private.
5. Switch the read-only status call first and compare the agent response with the current panel response.
6. Switch power actions and logs after status is stable.
7. Replace console commands with the allowlisted `/v1/command` route.
8. Design a separate file service with canonical-path checks, extension policy, maximum sizes, atomic writes, and an audit log.
9. Design backups and restore as separate operations with confirmation, lock files, retention, and a restore health check.
10. Remove the old Pterodactyl bridge only after the agent-backed flows have passed a full backup, restart, console, and player-join test.
