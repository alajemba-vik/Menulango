import { test } from "node:test";
import assert from "node:assert/strict";
import { GeminiSseExtractor } from "./sse.ts";

const event = (text: string, extra = "") =>
  `data: {"candidates":[{"content":{"parts":[{"text":${JSON.stringify(text)}}]}${extra}}]}\n\n`;

test("unwraps model text from events split at arbitrary points", () => {
  const stream = event('{"menu":') + event('{"language":"el"}}') +
    'data: {"candidates":[{"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1732,"candidatesTokenCount":5400}}\n\n';
  const extractor = new GeminiSseExtractor();
  const out: string[] = [];
  for (let i = 0; i < stream.length; i += 7) out.push(...extractor.push(stream.slice(i, i + 7)));
  out.push(...extractor.finish());

  assert.equal(out.join(""), '{"menu":{"language":"el"}}');
  assert.deepEqual(extractor.stats, { promptTokens: 1732, outputTokens: 5400, finishReason: "STOP" });
});

test("ignores thought parts and malformed events", () => {
  const extractor = new GeminiSseExtractor();
  const thought = 'data: {"candidates":[{"content":{"parts":[{"text":"hmm","thought":true},{"text":"{}"}]}}]}\n\n';
  assert.deepEqual(extractor.push("data: not json\n\n" + thought), ["{}"]);
});

test("flushes a final event without a trailing blank line", () => {
  const extractor = new GeminiSseExtractor();
  assert.deepEqual(extractor.push(event("]}").trimEnd()), []);
  assert.deepEqual(extractor.finish(), ["]}"]);
});
