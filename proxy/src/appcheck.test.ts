import { test } from "node:test";
import assert from "node:assert/strict";
import { verifyAppCheckToken } from "./appcheck.ts";

const PROJECT = "123456789";
const NOW = 1_800_000_000;

const { publicKey, privateKey } = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true,
  ["sign", "verify"],
);
const jwk = { ...(await crypto.subtle.exportKey("jwk", publicKey)), kid: "k1" };
const jwks = async () => [jwk];

const b64url = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const part = (value: object) => b64url(new TextEncoder().encode(JSON.stringify(value)));

async function sign(payload: object, header: object = { alg: "RS256", typ: "JWT", kid: "k1" }) {
  const unsigned = `${part(header)}.${part(payload)}`;
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", privateKey, new TextEncoder().encode(unsigned));
  return `${unsigned}.${b64url(new Uint8Array(signature))}`;
}

const good = {
  iss: `https://firebaseappcheck.googleapis.com/${PROJECT}`,
  aud: [`projects/${PROJECT}`, "projects/menulango"],
  sub: "1:123456789:ios:abc",
  exp: NOW + 3600,
};

test("accepts a token Firebase signed for this project", async () => {
  assert.deepEqual(await verifyAppCheckToken(await sign(good), PROJECT, jwks, NOW), { ok: true, appId: good.sub });
});

test("refuses expired, foreign, unsigned and tampered tokens", async () => {
  const reason = async (token: string) => {
    const result = await verifyAppCheckToken(token, PROJECT, jwks, NOW);
    return result.ok ? "ok" : result.reason;
  };
  assert.equal(await reason(await sign({ ...good, exp: NOW - 1 })), "expired");
  assert.equal(await reason(await sign({ ...good, aud: ["projects/999"] })), "audience");
  assert.equal(await reason(await sign({ ...good, iss: "https://firebaseappcheck.googleapis.com/999" })), "issuer");
  assert.equal(await reason(await sign(good, { alg: "none", typ: "JWT", kid: "k1" })), "header");
  assert.equal(await reason(await sign(good, { alg: "RS256", typ: "JWT", kid: "other" })), "unknown-key");
  const [h, , s] = (await sign(good)).split(".");
  assert.equal(await reason(`${h}.${part({ ...good, sub: "attacker" })}.${s}`), "signature");
  assert.equal(await reason("not-a-token"), "malformed");
});
