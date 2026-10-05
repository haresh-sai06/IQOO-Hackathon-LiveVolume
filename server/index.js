'use strict';

/**
 * Reference Agora RTC token service for LiveVolume.
 *
 * The Android client (see `CallTokenProvider.kt`) performs:
 *   GET {AGORA_TOKEN_SERVER_URL}?channel=<channel>&uid=<uid>
 * and expects `{ "token": "<rtc-token>" }`.
 *
 * This process must be deployed somewhere reachable over HTTPS by the devices,
 * because the Agora App Certificate must never be embedded in the APK.
 *
 * Run:
 *   npm install
 *   cp .env.example .env   # then fill in the Agora credentials
 *   npm start
 */

require('dotenv').config();

const express = require('express');

const PORT = Number(process.env.PORT || 8080);
const APP_ID = (process.env.AGORA_APP_ID || '').trim();
const APP_CERTIFICATE = (process.env.AGORA_APP_CERTIFICATE || '').trim();
const TOKEN_TTL_SECONDS = Number(process.env.TOKEN_TTL_SECONDS || 3600);

/** Agora allows 1-64 characters from a limited alphabet. */
const CHANNEL_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
const MAX_TOKEN_TTL_SECONDS = 86400;

if (!APP_ID || !APP_CERTIFICATE) {
  console.error(
    'Missing AGORA_APP_ID or AGORA_APP_CERTIFICATE.\n' +
      'Copy server/.env.example to server/.env and fill in your Agora credentials.'
  );
  process.exit(1);
}

/**
 * The `agora-token` package exposes the builder and role enum under slightly
 * different names across versions, so resolve them defensively.
 */
function loadTokenBuilder() {
  const candidates = [
    'agora-token',
    'agora-token/src/RtcTokenBuilder2',
    'agora-access-token'
  ];
  for (const candidate of candidates) {
    try {
      const mod = require(candidate);
      if (mod && mod.RtcTokenBuilder) {
        return { RtcTokenBuilder: mod.RtcTokenBuilder, RtcRole: mod.RtcRole || mod.Role };
      }
    } catch (_) {
      // Try the next candidate.
    }
  }
  throw new Error('Could not load an Agora token builder. Run `npm install` inside ./server.');
}

const { RtcTokenBuilder, RtcRole } = loadTokenBuilder();

const app = express();

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'livevolume-token-server' });
});

app.get('/rtc-token', (req, res) => {
  const channel = String(req.query.channel || '').trim();
  const uidRaw = String(req.query.uid === undefined ? '0' : req.query.uid).trim();
  const uid = Number.parseInt(uidRaw, 10);

  if (!CHANNEL_PATTERN.test(channel)) {
    return res.status(400).json({
      error: 'channel must be 1-64 characters from [A-Za-z0-9_-].'
    });
  }
  if (!Number.isInteger(uid) || uid < 0 || uid > 0xffffffff) {
    return res.status(400).json({
      error: 'uid must be an unsigned 32-bit integer.'
    });
  }

  const ttl = Math.min(
    Math.max(Number.isFinite(TOKEN_TTL_SECONDS) ? TOKEN_TTL_SECONDS : 3600, 60),
    MAX_TOKEN_TTL_SECONDS
  );

  try {
    const token = RtcTokenBuilder.buildTokenWithUid(
      APP_ID,
      APP_CERTIFICATE,
      channel,
      uid,
      RtcRole.PUBLISHER,
      ttl,
      ttl
    );
    return res.json({
      token,
      channel,
      uid,
      expiresInSeconds: ttl
    });
  } catch (err) {
    console.error('Failed to build an Agora token:', err);
    return res.status(500).json({ error: 'Unable to mint an RTC token.' });
  }
});

app.listen(PORT, () => {
  console.log(`LiveVolume token service listening on port ${PORT}`);
  console.log('  GET /health');
  console.log('  GET /rtc-token?channel=<name>&uid=<uid>');
});
