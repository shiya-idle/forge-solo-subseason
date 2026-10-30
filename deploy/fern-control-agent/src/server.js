#!/usr/bin/env node
'use strict';

const http = require('node:http');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');

const PORT = Number(process.env.FERN_AGENT_PORT || 8787);
const HOST = process.env.FERN_AGENT_HOST || '127.0.0.1';
const SERVICE = process.env.FERN_MINECRAFT_SERVICE || 'fern-minecraft.service';
const RCON_BIN = process.env.FERN_RCON_BIN || '/usr/local/bin/rcon-cli';
const SHARED_SECRET = process.env.FERN_AGENT_SECRET || '';
const MAX_SKEW_SECONDS = Number(process.env.FERN_AUTH_MAX_SKEW || 60);
const BODY_LIMIT = 64 * 1024;
const NONCE_TTL_MS = (MAX_SKEW_SECONDS + 30) * 1000;
const nonces = new Map();

if (!SHARED_SECRET || SHARED_SECRET.length < 32) {
  console.error('FERN_AGENT_SECRET must be set and at least 32 characters long.');
  process.exit(1);
}

function json(res, status, value) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  });
  res.end(body);
}

function cleanNonces() {
  const now = Date.now();
  for (const [nonce, expires] of nonces) if (expires <= now) nonces.delete(nonce);
}

function safeEqual(a, b) {
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function authenticate(req, rawBody) {
  cleanNonces();
  const timestamp = String(req.headers['x-fern-timestamp'] || '');
  const nonce = String(req.headers['x-fern-nonce'] || '');
  const signature = String(req.headers['x-fern-signature'] || '');
  if (!/^\d{10}$/.test(timestamp) || !/^[A-Za-z0-9_-]{16,96}$/.test(nonce) || !/^[a-f0-9]{64}$/i.test(signature)) return false;
  if (Math.abs(Math.floor(Date.now() / 1000) - Number(timestamp)) > MAX_SKEW_SECONDS) return false;
  if (nonces.has(nonce)) return false;
  const path = new URL(req.url, `http://${HOST}:${PORT}`).pathname;
  const canonical = `${timestamp}.${nonce}.${req.method}.${path}.${rawBody}`;
  const expected = crypto.createHmac('sha256', SHARED_SECRET).update(canonical).digest('hex');
  if (!safeEqual(expected, signature)) return false;
  nonces.set(nonce, Date.now() + NONCE_TTL_MS);
  return true;
}

function run(binary, args, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    const child = spawn(binary, args, { stdio: ['ignore', 'pipe', 'pipe'], shell: false });
    let stdout = '';
    let stderr = '';
    const timer = setTimeout(() => { child.kill('SIGKILL'); reject(new Error('command timed out')); }, timeoutMs);
    child.stdout.on('data', chunk => { stdout += chunk; if (stdout.length > 256 * 1024) child.kill('SIGKILL'); });
    child.stderr.on('data', chunk => { stderr += chunk; });
    child.on('error', error => { clearTimeout(timer); reject(error); });
    child.on('close', code => { clearTimeout(timer); if (code === 0) resolve({ stdout: stdout.trim(), stderr: stderr.trim() }); else reject(new Error(stderr.trim() || `command exited with ${code}`)); });
  });
}

async function serviceState() {
  try {
    const result = await run('/usr/bin/systemctl', ['is-active', SERVICE], 5000);
    return result.stdout || 'unknown';
  } catch { return 'inactive'; }
}

async function handle(req, res, rawBody) {
  const url = new URL(req.url, `http://${HOST}:${PORT}`);
  if (url.pathname === '/healthz' && req.method === 'GET') return json(res, 200, { ok: true, service: 'fern-control-agent' });
  if (!url.pathname.startsWith('/v1/')) return json(res, 404, { error: 'Not found.' });
  if (!authenticate(req, rawBody)) return json(res, 401, { error: 'Invalid or expired request signature.' });

  if (url.pathname === '/v1/status' && req.method === 'GET') {
    const state = await serviceState();
    return json(res, 200, { ok: true, service: SERVICE, current_state: state, address: process.env.FERN_SERVER_ADDRESS || null, agent_time: new Date().toISOString() });
  }

  if (url.pathname === '/v1/power' && req.method === 'POST') {
    let body; try { body = rawBody ? JSON.parse(rawBody) : {}; } catch { return json(res, 400, { error: 'Invalid JSON.' }); }
    const signal = String(body.signal || '');
    if (!['start', 'stop', 'restart'].includes(signal)) return json(res, 400, { error: 'Signal must be start, stop, or restart.' });
    try { await run('/usr/bin/systemctl', [signal, SERVICE], 30000); return json(res, 200, { ok: true, signal }); }
    catch (error) { return json(res, 502, { error: `Power action failed: ${error.message}` }); }
  }

  if (url.pathname === '/v1/command' && req.method === 'POST') {
    let body; try { body = rawBody ? JSON.parse(rawBody) : {}; } catch { return json(res, 400, { error: 'Invalid JSON.' }); }
    const command = String(body.command || '').trim();
    const allowed = /^(list|say |save-all|save-off|save-on|time |weather |difficulty |gamerule |whitelist (on|off|list|add |remove )|fwhitelist (on|off|list|add |remove ))/i;
    if (!command || command.length > 256 || !allowed.test(command)) return json(res, 403, { error: 'Command is not allowed by the Fern safety policy.' });
    try { const result = await run(RCON_BIN, [command], 15000); return json(res, 200, { ok: true, output: result.stdout }); }
    catch (error) { return json(res, 502, { error: `Command failed: ${error.message}` }); }
  }

  if (url.pathname === '/v1/logs' && req.method === 'GET') {
    const lines = Math.min(500, Math.max(20, Number(url.searchParams.get('lines') || 100)));
    try { const result = await run('/usr/bin/journalctl', ['-u', SERVICE, '-n', String(lines), '--no-pager', '-o', 'cat'], 10000); return json(res, 200, { ok: true, lines: result.stdout.split('\n') }); }
    catch (error) { return json(res, 502, { error: `Log read failed: ${error.message}` }); }
  }

  return json(res, 404, { error: 'Unknown agent route.' });
}

const server = http.createServer((req, res) => {
  if (req.method !== 'GET' && req.method !== 'POST') return json(res, 405, { error: 'Method not allowed.' });
  let size = 0; const chunks = [];
  req.on('data', chunk => { size += chunk.length; if (size <= BODY_LIMIT) chunks.push(chunk); else req.destroy(); });
  req.on('end', () => handle(req, res, Buffer.concat(chunks).toString('utf8')).catch(error => { console.error(error); if (!res.headersSent) json(res, 500, { error: 'Agent request failed.' }); }));
});
server.listen(PORT, HOST, () => console.log(`Fern Control Agent listening on ${HOST}:${PORT}`));
