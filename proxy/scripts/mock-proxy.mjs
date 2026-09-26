#!/usr/bin/env node
// A stand-in for the Worker that needs no Cloudflare account and no Gemini key.
// It honours the same contract as src/index.ts and streams the bundled sample menu slowly,
// the way the real model writes, so every app state can be exercised locally.
//
//   node proxy/scripts/mock-proxy.mjs                  # serves the sample menu
//   MOCK_ERROR=RATE_LIMITED node proxy/scripts/mock-proxy.mjs   # or UNREADABLE / UPSTREAM
//   MOCK_EMPTY=1 node proxy/scripts/mock-proxy.mjs     # a photo with no menu in it
//   MOCK_CUT=1 node proxy/scripts/mock-proxy.mjs       # the stream dies halfway
//   MOCK_PAGES=3 node proxy/scripts/mock-proxy.mjs     # each scan is the next of 3 pages of the sample
//
// Android emulator:  adb reverse tcp:8787 tcp:8787, then PROXY_URL=http://localhost:8787 (debug builds only).
import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const port = Number(process.env.PORT ?? 8787);
const sample = readFileSync(
  fileURLToPath(new URL("../../composeApp/src/commonMain/composeResources/files/sample_menu.json", import.meta.url)),
);
const status = { RATE_LIMITED: 429, UNREADABLE: 422, UPSTREAM: 502 };
const pages = Number(process.env.MOCK_PAGES ?? 0);
let scans = 0;

/** One page of the sample: its share of the dishes, in order, as a menu of its own. */
function samplePage(index) {
  const menu = JSON.parse(sample.toString("utf8"));
  const per = Math.ceil(menu.dishes.length / pages);
  menu.dishes = menu.dishes.slice(index * per, (index + 1) * per);
  return Buffer.from(JSON.stringify(menu));
}

createServer((req, res) => {
  if (req.method !== "POST" || req.url !== "/scan") return res.writeHead(404).end("Not found");
  let size = 0;
  req.on("data", (chunk) => (size += chunk.length));
  req.on("end", async () => {
    console.log(`scan: ${size} bytes from device ${req.headers["x-device-id"]}`);
    const error = process.env.MOCK_ERROR;
    if (error) return res.writeHead(status[error] ?? 502, { "Content-Type": "application/json" }).end(JSON.stringify({ error }));
    res.writeHead(200, { "Content-Type": "application/json; charset=utf-8" });
    if (process.env.MOCK_EMPTY) return res.end('{"menu":{"language":"en","truncated":false,"confidence":0},"dishes":[]}');
    const page = pages > 0 ? samplePage(scans++ % pages) : sample;
    const body = process.env.MOCK_CUT ? page.subarray(0, Math.floor(page.length / 2)) : page;
    await new Promise((r) => setTimeout(r, 900)); // time to first token
    for (let i = 0; i < body.length; i += 160) {
      res.write(body.subarray(i, i + 160));
      await new Promise((r) => setTimeout(r, 12));
    }
    res.end();
  });
}).listen(port, () => console.log(`mock proxy on http://localhost:${port}`));
