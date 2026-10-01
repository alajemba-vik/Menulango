/**
 * Firebase App Check: proof that a request comes from the real MenuLango app on a real device.
 *
 * The app gets a short-lived token from Apple App Attest or Google Play Integrity (through
 * Firebase) and sends it as `X-Firebase-AppCheck`. A script cannot mint one, so it cannot dodge the
 * per-device limits by inventing device ids. The token is an RS256 JWT signed by Firebase; its
 * public keys are published at [JWKS_URL].
 */

export const JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";

/** Firebase rotates keys well within this; a missing kid forces a refetch anyway. */
const JWKS_TTL_MS = 6 * 60 * 60 * 1000;

interface Jwk extends JsonWebKey {
  kid?: string;
}

export type JwksSource = () => Promise<Jwk[]>;

let cachedKeys: { keys: Jwk[]; fetchedAt: number } | null = null;

/** Firebase's published keys, kept in the isolate for [JWKS_TTL_MS]. */
export const firebaseJwks = async (fresh = false): Promise<Jwk[]> => {
  if (!fresh && cachedKeys && Date.now() - cachedKeys.fetchedAt < JWKS_TTL_MS) return cachedKeys.keys;
  const response = await fetch(JWKS_URL);
  if (!response.ok) throw new Error(`jwks ${response.status}`);
  const { keys } = (await response.json()) as { keys: Jwk[] };
  cachedKeys = { keys, fetchedAt: Date.now() };
  return keys;
};

export type AppCheckResult = { ok: true; appId: string } | { ok: false; reason: string };

/**
 * Checks signature, issuer, audience and expiry. [projectNumber] is the Firebase project's number
 * (not its id); [jwks] is swappable so tests can sign their own tokens.
 */
export async function verifyAppCheckToken(
  token: string,
  projectNumber: string,
  jwks: (fresh: boolean) => Promise<Jwk[]> = firebaseJwks,
  nowSeconds: number = Math.floor(Date.now() / 1000),
): Promise<AppCheckResult> {
  const parts = token.split(".");
  if (parts.length !== 3) return { ok: false, reason: "malformed" };
  const [headerPart, payloadPart, signaturePart] = parts;

  let header: { alg?: string; typ?: string; kid?: string };
  let payload: { iss?: string; aud?: string[] | string; exp?: number; sub?: string };
  try {
    header = JSON.parse(decodeText(headerPart));
    payload = JSON.parse(decodeText(payloadPart));
  } catch {
    return { ok: false, reason: "malformed" };
  }
  if (header.alg !== "RS256" || header.typ !== "JWT" || !header.kid) return { ok: false, reason: "header" };

  let key = (await jwks(false)).find((k) => k.kid === header.kid);
  if (!key) key = (await jwks(true)).find((k) => k.kid === header.kid);
  if (!key) return { ok: false, reason: "unknown-key" };

  const publicKey = await crypto.subtle.importKey(
    "jwk",
    { kty: key.kty, n: key.n, e: key.e, alg: "RS256", ext: true },
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  const signed = new TextEncoder().encode(`${headerPart}.${payloadPart}`);
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, decodeBytes(signaturePart), signed);
  if (!valid) return { ok: false, reason: "signature" };

  if (payload.iss !== `https://firebaseappcheck.googleapis.com/${projectNumber}`) return { ok: false, reason: "issuer" };
  const audience = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
  if (!audience.includes(`projects/${projectNumber}`)) return { ok: false, reason: "audience" };
  if (typeof payload.exp !== "number" || payload.exp <= nowSeconds) return { ok: false, reason: "expired" };
  if (!payload.sub) return { ok: false, reason: "subject" };

  return { ok: true, appId: payload.sub };
}

function decodeBytes(base64url: string): Uint8Array {
  const base64 = base64url.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(base64 + "=".repeat((4 - (base64.length % 4)) % 4));
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

function decodeText(base64url: string): string {
  return new TextDecoder().decode(decodeBytes(base64url));
}
