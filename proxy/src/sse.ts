/**
 * Turns Gemini's server-sent events into the plain JSON text the model is writing.
 *
 * The app expects the response schema as one JSON document. Gemini streams that document in
 * pieces wrapped in SSE envelopes; this unwraps them so the app receives the document itself,
 * progressively, and can show each dish the moment it is complete.
 */
export interface StreamStats {
  promptTokens: number;
  outputTokens: number;
  finishReason: string | null;
}

export class GeminiSseExtractor {
  private pending = "";
  readonly stats: StreamStats = { promptTokens: 0, outputTokens: 0, finishReason: null };

  /** Feeds raw SSE text; returns the model text contained in every event completed so far. */
  push(chunk: string): string[] {
    this.pending += chunk.replace(/\r\n/g, "\n");
    const texts: string[] = [];
    let boundary: number;
    while ((boundary = this.pending.indexOf("\n\n")) >= 0) {
      const event = this.pending.slice(0, boundary);
      this.pending = this.pending.slice(boundary + 2);
      const text = this.readEvent(event);
      if (text) texts.push(text);
    }
    return texts;
  }

  /** Flushes a final event that arrived without a trailing blank line. */
  finish(): string[] {
    const rest = this.pending.trim();
    this.pending = "";
    const text = rest ? this.readEvent(rest) : null;
    return text ? [text] : [];
  }

  private readEvent(event: string): string | null {
    const data = event
      .split("\n")
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).trimStart())
      .join("\n");
    if (!data) return null;

    let payload: GeminiChunk;
    try {
      payload = JSON.parse(data);
    } catch {
      return null;
    }
    const usage = payload.usageMetadata;
    if (usage) {
      this.stats.promptTokens = usage.promptTokenCount ?? this.stats.promptTokens;
      this.stats.outputTokens = usage.candidatesTokenCount ?? this.stats.outputTokens;
    }
    const candidate = payload.candidates?.[0];
    if (candidate?.finishReason) this.stats.finishReason = candidate.finishReason;
    const text = (candidate?.content?.parts ?? [])
      .filter((part) => !part.thought)
      .map((part) => part.text ?? "")
      .join("");
    return text || null;
  }
}

interface GeminiChunk {
  candidates?: {
    content?: { parts?: { text?: string; thought?: boolean }[] };
    finishReason?: string;
  }[];
  usageMetadata?: { promptTokenCount?: number; candidatesTokenCount?: number };
}
