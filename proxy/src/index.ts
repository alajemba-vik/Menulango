/**
 * MenuLango proxy.
 *
 * The app never calls Gemini directly: a key shipped inside an APK or IPA can be extracted in
 * minutes. This Worker holds the key, limits each device, and streams the model's JSON back
 * unchanged. It is also the one place to change model or prompt without an app release.
 *
 * Contract (see README.md):
 *   POST /scan   headers: X-Device-Id   body: { "image": "<base64 jpeg>", "locale": "fr-FR" }
 *   200 → the menu schema, streamed as it is written
 *   4xx/5xx → { "error": "RATE_LIMITED" | "UNREADABLE" | "UPSTREAM" }
 */
import { GeminiSseExtractor } from "./sse.ts";
import { PRIVACY_HTML } from "./privacy.ts";
import { localeInstruction, MAX_OUTPUT_TOKENS, RESPONSE_SCHEMA, SYSTEM_PROMPT } from "./prompt.ts";

export interface Env {
  GEMINI_API_KEY: string;
  GEMINI_MODEL: string;
  SCANS_PER_HOUR: string;
  SCAN_BURST: RateLimit;
}

type ErrorCode = "RATE_LIMITED" | "UNREADABLE" | "UPSTREAM";

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
    if (url.pathname !== "/scan" || request.method !== "POST") return new Response("Not found", { status: 404 });
    return scan(request, env, ctx);
  },
} satisfies ExportedHandler<Env>;

async function scan(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const deviceId = request.headers.get("X-Device-Id") ?? "";
  if (!DEVICE_ID.test(deviceId)) return fail(400, "UNREADABLE");

  const declaredLength = Number(request.headers.get("Content-Length") ?? "0");
  if (declaredLength > MAX_BODY_BYTES) return fail(413, "UNREADABLE");

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
