# LiveVolume token service

A tiny reference implementation of the Agora RTC token endpoint the Android app
calls. Production Agora projects must use token authentication, and the App
Certificate can never be shipped inside an APK — so tokens are minted here.

## Contract

The Android client (`app/src/main/java/com/example/call/CallTokenProvider.kt`)
performs:

```
GET {AGORA_TOKEN_SERVER_URL}?channel=<channel>&uid=<uid>
```

and expects:

```json
{ "token": "<rtc-token>" }
```

Anything else (non-200, missing `token`) makes the app fall back to joining
without a token, which only works for Agora projects with certificate
authentication disabled.

## Run locally

```bash
cd server
npm install
cp .env.example .env      # fill in AGORA_APP_ID + AGORA_APP_CERTIFICATE
npm start
```

Then verify:

```bash
curl "http://localhost:8080/health"
curl "http://localhost:8080/rtc-token?channel=livevolume_demo&uid=1234"
```

## Point the app at it

Put the public URL in the repo-root `.env` (or in `gradle.properties`):

```properties
AGORA_TOKEN_SERVER_URL=https://your-host.example.com/rtc-token
```

Rebuild the app; `BuildConfig.AGORA_TOKEN_SERVER_URL` picks the value up and
`CallTokenProvider` starts requesting real tokens.

## Deploy

Any container/Node host works (Cloud Run, Fly, Render, a small VM). The service
is stateless and needs only the two Agora credentials. Serve it over HTTPS —
the client rejects nothing today, but a plain-HTTP endpoint would leak tokens on
the wire.

### Environment variables

| Variable | Required | Description |
| --- | --- | --- |
| `AGORA_APP_ID` | yes | Same App ID as the Android app. |
| `AGORA_APP_CERTIFICATE` | yes | Agora project's primary certificate. **Server-side only.** |
| `PORT` | no | Listen port, default `8080`. |
| `TOKEN_TTL_SECONDS` | no | Token lifetime, default `3600`, capped at `86400`. |

## Security notes

- Tokens are scoped to a single channel + uid for `TOKEN_TTL_SECONDS`.
- Nothing is persisted; no user data flows through this service.
- Add authentication and rate limiting before exposing it publicly, so random
  callers cannot mint tokens for channels they were never invited to.
