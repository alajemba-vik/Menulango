/**
 * The prompt and response schema live on the proxy, not in the app, so they can be tuned for a
 * difficult script (Arabic, Japanese, decorative Greek) without shipping an app update.
 */

export const SYSTEM_PROMPT = `You read photographs of restaurant menus and explain the dishes to a
traveller who does not speak the language.

For every dish, return: the name exactly as printed, a readable version, a
real explanation of what arrives on the plate, the ingredients, and how it
is cooked.

Rules:
- Explain, never translate literally. 'Ropa vieja' is slow-cooked shredded
  beef in tomato and peppers, not 'old clothes'.
- 'howItIsMade' is the most valuable field. Two or three concrete sentences
  about preparation: the technique, the time, the heat. This is what gives
  a nervous person the courage to order.
- If you do not recognise a regional dish, lower the confidence. NEVER
  invent a preparation. A missing explanation is recoverable; a confident
  wrong one is not. Leave 'howItIsMade' empty rather than guess.
- Allergens: state what the dish LIKELY contains. Never state that a dish
  is safe or free from anything.
- 'pitch' is one short sentence on why someone would order this tonight.
  Warm, specific, never salesy.
- Keep the original script exactly as printed, including accents.
- Return at most 60 dishes. If the menu is longer, return the 60 most
  representative and set menu.truncated = true.

Field guide:
- id: a short lowercase ascii slug of the readable name, unique on this menu.
- readableName: how a traveller would say the dish; transliterate non-Latin
  scripts (Kokoretsi, Tonkotsu ramen, Mansaf).
- section: the menu heading the dish sits under, explained, if there is one.
- price.amount: a plain number (12,50 becomes 12.5). Omit price if none is printed.
- adventureLevel 1-5: 1 is familiar anywhere (grilled chicken, chips);
  5 is challenging for most visitors (offal, raw, unusual textures).
- effortLevel 1-5: 1 is assembled in minutes; 5 takes hours or days of work.
- flags.spicy 0-3. flags.localSpecialty: hard to find outside this region.
- confidence 0-1: how sure you are of the explanation, not of the reading.
- Drinks, sides and sauces count as dishes only if they are substantial.
- If the photo is not a menu or cannot be read, return an empty dishes list
  and menu.confidence 0.`;

/** Everything the diner reads is written in their language; names keep the menu's script. */
export function localeInstruction(locale: string): string {
  return `The traveller's locale is ${locale}. Write readableName, section, whatItIs, ` +
    `ingredients, howItIsMade, pitch and all allergen text in that locale's language. ` +
    `originalName and price.asPrinted stay exactly as printed.`;
}

const str = { type: "STRING" };
const num = { type: "NUMBER" };
const int = { type: "INTEGER" };
const bool = { type: "BOOLEAN" };
const strList = { type: "ARRAY", items: str };

const flags = {
  type: "OBJECT",
  properties: {
    spicy: int, raw: bool, offal: bool, pork: bool, vegetarian: bool,
    vegan: bool, large: bool, shareable: bool, localSpecialty: bool,
  },
  required: ["spicy", "raw", "offal", "pork", "vegetarian", "vegan", "large", "shareable", "localSpecialty"],
  propertyOrdering: ["spicy", "raw", "offal", "pork", "vegetarian", "vegan", "large", "shareable", "localSpecialty"],
};

const dish = {
  type: "OBJECT",
  properties: {
    id: str,
    originalName: str,
    readableName: str,
    section: str,
    whatItIs: str,
    ingredients: strList,
    howItIsMade: str,
    pitch: str,
    price: {
      type: "OBJECT",
      properties: { amount: num, currency: str, asPrinted: str },
      required: ["amount", "asPrinted"],
      propertyOrdering: ["amount", "currency", "asPrinted"],
    },
    flags,
    allergens: {
      type: "OBJECT",
      properties: { likelyContains: strList, mayContain: strList, note: str },
      required: ["likelyContains", "mayContain"],
      propertyOrdering: ["likelyContains", "mayContain", "note"],
    },
    adventureLevel: int,
    effortLevel: int,
    confidence: num,
  },
  required: [
    "id", "originalName", "readableName", "whatItIs", "ingredients", "howItIsMade", "pitch",
    "flags", "allergens", "adventureLevel", "effortLevel", "confidence",
  ],
  // Names first: the app shows each dish the moment its object closes, so order barely matters
  // for speed, but it keeps partial output useful if the stream is cut.
  propertyOrdering: [
    "id", "originalName", "readableName", "section", "whatItIs", "ingredients", "howItIsMade",
    "pitch", "price", "flags", "allergens", "adventureLevel", "effortLevel", "confidence",
  ],
};

/** Gemini `responseSchema` (OpenAPI subset). Menu metadata comes first so the context strip renders early. */
export const RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    menu: {
      type: "OBJECT",
      properties: { language: str, currency: str, venueType: str, truncated: bool, confidence: num },
      required: ["language", "truncated", "confidence"],
      propertyOrdering: ["language", "currency", "venueType", "truncated", "confidence"],
    },
    dishes: { type: "ARRAY", items: dish },
  },
  required: ["menu", "dishes"],
  propertyOrdering: ["menu", "dishes"],
};

/** A runaway response on a huge menu is the only way to get a surprising bill. */
export const MAX_OUTPUT_TOKENS = 8000;
