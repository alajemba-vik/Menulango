/**
 * MenuLango proxy.
 *
 * The app never calls Gemini directly: a key shipped inside an APK or IPA can be extracted in
 * minutes. This Worker holds the key, limits each device, and streams the model's JSON back
 * unchanged. It is also the one place to change model or prompt without an app release.
 *
 * Contract (see README.md):
 *   POST /scan   headers: X-Device-Id, X-Firebase-AppCheck   body: { "image": "<base64 jpeg>", "locale": "fr-FR" }
 *   200 → the menu schema, streamed as it is written
 *   4xx/5xx → { "error": "RATE_LIMITED" | "UNREADABLE" | "UPSTREAM" | "UNVERIFIED" }
 *   GET  /photo?title=Moussaka        → { "photo": DishPhoto | null }  (Wikimedia Commons, cached)
 *   GET  /photo/image?src=<commons>  → the image bytes, relayed and cached
 *   POST /share  { title, locale, menu }  → { "url": "…/m/<id>", "expiresInSeconds": 86400 }
 *   GET  /m/<id>                       → the shared menu as a web page, for 24 hours
 *   GET  /rates                        → { "base": "USD", "updated": <unix s>, "rates": { "EUR": 0.92, … } }
 */
import { verifyAppCheckToken } from "./appcheck.ts";
import { findDishPhoto, WIKIMEDIA_HEADERS } from "./photo.ts";
import {
  MAX_SHARE_BYTES,
  newShareId,
  parseShare,
  renderGonePage,
  renderSharePage,
  SHARE_ID,
  SHARE_TTL_S,
  type SharedMenu,
} from "./share.ts";
import { GeminiSseExtractor } from "./sse.ts";
import { PRIVACY_HTML } from "./privacy.ts";
import { localeInstruction, MAX_OUTPUT_TOKENS, RESPONSE_SCHEMA, SYSTEM_PROMPT } from "./prompt.ts";

export interface Env {
  GEMINI_API_KEY: string;
  GEMINI_MODEL: string;
  SCANS_PER_HOUR: string;
  SCAN_BURST: RateLimit;
  /** "enforce" rejects requests without a valid App Check token; "monitor" only logs them; "off" skips the check. */
  APP_CHECK?: string;
  /** The Firebase project's number (Project settings → General), not its id. */
  FIREBASE_PROJECT_NUMBER?: string;
  /** Menus shared with a table, each kept for a day. */
  SHARES: KVNamespace;
}

type ErrorCode = "RATE_LIMITED" | "UNREADABLE" | "UPSTREAM" | "UNVERIFIED";

/** The client resizes to 1536px / JPEG 80 (~300 KB). Anything this large is a bug or abuse. */
const MAX_BODY_BYTES = 1_500_000;
const DEVICE_ID = /^[A-Za-z0-9-]{8,64}$/;
const LOCALE = /^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/;

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/health") return new Response("ok");
    if (url.pathname === "/privacy") {
      return new Response(PRIVACY_HTML, { headers: { "Content-Type": "text/html; charset=utf-8" } });
    }
    if (request.method === "GET" && url.pathname.startsWith("/m/")) return sharedPage(url, env);
    if (request.method === "POST" && url.pathname === "/share") return share(url, request, env, ctx);
    if (request.method === "GET" && url.pathname === "/photo") return photo(url, request, env, ctx);
    if (request.method === "GET" && url.pathname === "/photo/image") return photoImage(url, request, env, ctx);
    if (request.method === "GET" && url.pathname === "/rates") return rates(request, env, ctx);
    if (url.pathname !== "/scan" || request.method !== "POST") return new Response("Not found", { status: 404 });
    return scan(request, env, ctx);
  },
} satisfies ExportedHandler<Env>;

/**
 * POST /share: keeps the explained menu for a day under a new, unguessable id, and answers with
 * the link to open it. Same checks as scanning (a real app, a device id, the burst limit), and a
 * size cap: a menu is text, never large.
 */
async function share(url: URL, request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const deviceId = request.headers.get("X-Device-Id") ?? "";
  if (!DEVICE_ID.test(deviceId)) return fail(400, "UNREADABLE");
  if (Number(request.headers.get("Content-Length") ?? "0") > MAX_SHARE_BYTES) return fail(413, "UNREADABLE");
  if (!(await fromTheApp(request, env))) return fail(401, "UNVERIFIED");
  if (!(await withinLimits(deviceId, env, ctx))) return fail(429, "RATE_LIMITED");

  const raw = await request.text();
  if (raw.length > MAX_SHARE_BYTES) return fail(413, "UNREADABLE");
  let body: unknown;
  try {
    body = JSON.parse(raw);
  } catch {
    return fail(400, "UNREADABLE");
  }
  const menu = parseShare(body, Date.now());
  if (!menu) return fail(400, "UNREADABLE");

  const id = newShareId();
  await env.SHARES.put(id, JSON.stringify(menu), { expirationTtl: SHARE_TTL_S });
  return new Response(JSON.stringify({ url: `${url.origin}/m/${id}`, expiresInSeconds: SHARE_TTL_S }), {
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

/** GET /m/<id>: the shared menu, or a kind "this has expired" page once its day is over. */
async function sharedPage(url: URL, env: Env): Promise<Response> {
  const id = url.pathname.slice("/m/".length);
  const stored = SHARE_ID.test(id) ? await env.SHARES.get(id) : null;
  const locale = (url.searchParams.get("hl") ?? "en").slice(0, 12);
  if (!stored) return html(renderGonePage(locale), 404);
  let menu: SharedMenu;
  try {
    menu = JSON.parse(stored) as SharedMenu;
  } catch {
    return html(renderGonePage(locale), 404);
  }
  return html(renderSharePage(menu), 200);
}

/**
 * A page with no scripts, no fetched fonts or images, not indexed, not framed, and cached only
 * briefly so an expired link stops working on time.
 */
function html(page: string, status: number): Response {
  return new Response(page, {
    status,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
      "X-Robots-Tag": "noindex, nofollow",
      "Referrer-Policy": "no-referrer",
      "X-Content-Type-Options": "nosniff",
      "Cache-Control": "private, max-age=300",
    },
  });
}

/**
 * GET /photo?title=Moussaka → { "photo": DishPhoto | null }. A dish's Commons photo, looked up
 * once and cached for everyone, "no photo" included, so each dish costs Wikimedia one visit.
 */
async function photo(url: URL, request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const title = (url.searchParams.get("title") ?? "").trim();
  if (!TITLE.test(title)) return fail(400, "UNREADABLE");
  if (!(await fromTheApp(request, env))) return fail(401, "UNVERIFIED");

  const key = new Request(`https://menulango.internal/photo/${PHOTO_CACHE_VERSION}/${encodeURIComponent(title.toLowerCase())}`);
  const cached = await caches.default.match(key);
  if (cached) return cached;

  const found = await findDishPhoto(title).catch(() => undefined);
  // A failed lookup is not remembered as "no photo": only real answers are cached.
  if (found === undefined) return fail(502, "UPSTREAM");
  const response = new Response(JSON.stringify({ photo: found }), {
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": `public, max-age=${PHOTO_CACHE_S}` },
  });
  ctx.waitUntil(caches.default.put(key, response.clone()));
  return response;
}

/**
 * GET /photo/image?src=<Wikimedia image url> → the image bytes. Relayed and cached here so a
 * diner's phone never talks to Wikimedia itself, and only Wikimedia's image hosts are allowed.
 */
async function photoImage(url: URL, request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const src = url.searchParams.get("src") ?? "";
  let target: URL;
  try {
    target = new URL(src);
  } catch {
    return fail(400, "UNREADABLE");
  }
  if (target.protocol !== "https:" || !WIKIMEDIA_IMAGE_HOSTS.includes(target.hostname)) return fail(400, "UNREADABLE");
  if (!(await fromTheApp(request, env))) return fail(401, "UNVERIFIED");

  const key = new Request(`https://menulango.internal/photo-image/${encodeURIComponent(target.toString())}`);
  const cached = await caches.default.match(key);
  if (cached) return cached;

  const upstream = await fetch(target.toString(), { headers: { "User-Agent": WIKIMEDIA_HEADERS["User-Agent"] } });
  const type = upstream.headers.get("Content-Type") ?? "";
  if (!upstream.ok || !type.startsWith("image/")) return fail(502, "UPSTREAM");
  const response = new Response(upstream.body, {
    headers: { "Content-Type": type, "Cache-Control": `public, max-age=${PHOTO_CACHE_S}` },
  });
  ctx.waitUntil(caches.default.put(key, response.clone()));
  return response;
}

/**
 * GET /rates: every currency against the US dollar, for showing a menu's prices in the diner's
 * own currency. Fetched from ExchangeRate-API's free open endpoint (daily rates, attribution
 * shown in the app) at most every few hours and shared by everyone, so phones never call it.
 */
async function rates(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  if (!(await fromTheApp(request, env))) return fail(401, "UNVERIFIED");
  const key = new Request("https://menulango.internal/rates/v1");
  const cached = await caches.default.match(key);
  if (cached) return cached;

  const upstream = await fetch("https://open.er-api.com/v6/latest/USD");
  if (!upstream.ok) return fail(502, "UPSTREAM");
  const body = (await upstream.json().catch(() => null)) as
    | { result?: string; time_last_update_unix?: number; rates?: Record<string, number> }
    | null;
  if (body?.result !== "success" || !body.rates) return fail(502, "UPSTREAM");
  const response = new Response(
    JSON.stringify({ base: "USD", updated: body.time_last_update_unix ?? 0, rates: body.rates }),
    { headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": `public, max-age=${RATES_CACHE_S}` } },
  );
  ctx.waitUntil(caches.default.put(key, response.clone()));
  return response;
}

/** The source updates once a day; a few hours' delay is fine for "roughly what this costs me". */
const RATES_CACHE_S = 6 * 60 * 60;

/** Wikipedia titles are short and plain; anything else is not a title. */
const TITLE = /^[^<>[\]{}|#\n]{1,120}$/;
const PHOTO_CACHE_S = 30 * 24 * 60 * 60;
/** Originals come from upload., resized copies from thumb. Nothing else is ever fetched. */
const WIKIMEDIA_IMAGE_HOSTS = ["upload.wikimedia.org", "thumb.wikimedia.org"];
/** Bumped when the lookup changes, so answers from an older lookup are never served again. */
const PHOTO_CACHE_VERSION = "v2";

async function scan(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const deviceId = request.headers.get("X-Device-Id") ?? "";
  if (!DEVICE_ID.test(deviceId)) return fail(400, "UNREADABLE");

  const declaredLength = Number(request.headers.get("Content-Length") ?? "0");
  if (declaredLength > MAX_BODY_BYTES) return fail(413, "UNREADABLE");

  if (!(await fromTheApp(request, env))) return fail(401, "UNVERIFIED");

  if (!(await withinLimits(deviceId, env, ctx))) return fail(429, "RATE_LIMITED");

  const raw = await request.arrayBuffer();
  if (raw.byteLength > MAX_BODY_BYTES) return fail(413, "UNREADABLE");

  let body: { image?: unknown; locale?: unknown };
  try {
    body = JSON.parse(new TextDecoder().decode(raw));
  } catch {
    return fail(400, "UNREADABLE");
  }
  const image = typeof body.image === "string" ? body.image : "";
  const locale = typeof body.locale === "string" && LOCALE.test(body.locale) ? body.locale : "en-US";
  if (image.length < 100) return fail(400, "UNREADABLE");

  const upstream = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${env.GEMINI_MODEL}:streamGenerateContent?alt=sse`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents: [
          {
            role: "user",
            parts: [{ inlineData: { mimeType: "image/jpeg", data: image } }, { text: localeInstruction(locale) }],
          },
        ],
        generationConfig: {
          responseMimeType: "application/json",
          responseSchema: RESPONSE_SCHEMA,
          maxOutputTokens: MAX_OUTPUT_TOKENS,
          temperature: 0.2,
          thinkingConfig: { thinkingLevel: "minimal" },
        },
      }),
    },
  );

  if (!upstream.ok || !upstream.body) {
    const detail = await upstream.text().catch(() => "");
    console.error(JSON.stringify({ event: "upstream_error", status: upstream.status, detail: detail.slice(0, 500) }));
    // Gemini rejects undecodable images with 400; everything else is on us or on them.
    return fail(upstream.status === 400 ? 422 : 502, upstream.status === 400 ? "UNREADABLE" : "UPSTREAM");
  }

  const extractor = new GeminiSseExtractor();
  const decoder = new TextDecoder();
  const encoder = new TextEncoder();
  const startedAt = Date.now();

  const unwrap = new TransformStream<Uint8Array, Uint8Array>({
    transform(chunk, controller) {
      for (const text of extractor.push(decoder.decode(chunk, { stream: true }))) controller.enqueue(encoder.encode(text));
    },
    flush(controller) {
      for (const text of extractor.finish()) controller.enqueue(encoder.encode(text));
      // One of the three numbers worth measuring: tokens per scan. Visible in Workers Logs.
      console.log(
        JSON.stringify({
          event: "scan",
          model: env.GEMINI_MODEL,
          promptTokens: extractor.stats.promptTokens,
          outputTokens: extractor.stats.outputTokens,
          finishReason: extractor.stats.finishReason,
          ms: Date.now() - startedAt,
        }),
      );
    },
  });

  return new Response(upstream.body.pipeThrough(unwrap), {
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

/**
 * App Check, before the limits so forged requests do not use up a real device's allowance. In
 * "monitor" every request passes and failures are only logged: useful while builds without the
 * token are still in use.
 */
async function fromTheApp(request: Request, env: Env): Promise<boolean> {
  const mode = env.APP_CHECK ?? "off";
  if (mode === "off" || !env.FIREBASE_PROJECT_NUMBER) return true;
  const token = request.headers.get("X-Firebase-AppCheck");
  const result = token
    ? await verifyAppCheckToken(token, env.FIREBASE_PROJECT_NUMBER).catch(() => ({ ok: false as const, reason: "jwks" }))
    : { ok: false as const, reason: "missing" };
  if (!result.ok) console.warn(JSON.stringify({ event: "app_check_failed", reason: result.reason, mode }));
  return result.ok || mode !== "enforce";
}

/**
 * Two limits: a strict burst limit through the rate-limiting binding (periods of 10 or 60 s
 * only), and a best-effort hourly cap counted in the per-location Cache API. The hourly counter is
 * not atomic and not global — it only has to make a leaked device id uneconomic to abuse.
 */
async function withinLimits(deviceId: string, env: Env, ctx: ExecutionContext): Promise<boolean> {
  const { success } = await env.SCAN_BURST.limit({ key: deviceId });
  if (!success) return false;

  const hour = Math.floor(Date.now() / 3_600_000);
  const counterUrl = `https://menulango.internal/hourly/${deviceId}/${hour}`;
  const cache = caches.default;
  const cached = await cache.match(counterUrl);
  const count = cached ? Number(await cached.text()) || 0 : 0;
  if (count >= Number(env.SCANS_PER_HOUR || "30")) return false;
  ctx.waitUntil(
    cache.put(counterUrl, new Response(String(count + 1), { headers: { "Cache-Control": "max-age=3600" } })),
  );
  return true;
}

function fail(status: number, error: ErrorCode): Response {
  return new Response(JSON.stringify({ error }), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
