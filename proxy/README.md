# MenuLango proxy

A Cloudflare Worker that holds the Gemini key so the app never does. See the main
[README](../README.md#deploy-the-proxy) to deploy it.

## Contract

```
POST /scan
  X-Device-Id: <random per-install id>
  { "image": "<base64 JPEG>", "locale": "fr-FR" }

200  application/json, streamed: { "menu": {…}, "dishes": [ … ] }   (schema in src/prompt.ts)
4xx/5xx  { "error": "RATE_LIMITED" | "UNREADABLE" | "UPSTREAM" }

GET /privacy   the privacy policy
GET /health    ok
```

- Bodies over 1.5 MB are refused; the app sends ~300 KB.
- 4 scans a minute (rate-limiting binding) and ~30 an hour (Cache API counter) per device.
- Responses stream as the model writes them; the app shows each dish as soon as it is complete.
- `npx wrangler tail` shows one line per scan with prompt and output tokens.

## Local

`node scripts/mock-proxy.mjs` serves the bundled sample menu with the same contract — no key needed.
`npm run dev` runs the real Worker locally (put a key in `.dev.vars`, see `.dev.vars.example`).
