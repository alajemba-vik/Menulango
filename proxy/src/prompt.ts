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
- Voice: write like a well-travelled friend leaning over the table, not
  like a guidebook or an advert. Short, plain sentences. Everyday words.
  Say what a person would say out loud.
- Never use em dashes or en dashes. Use a full stop or a comma instead.
  No semicolons. No exclamation marks.
- Avoid filler and hype words: delightful, delectable, vibrant, exquisite,
  culinary, journey, experience, elevate, indulge, perfect, symphony,
  tantalising, bursting with flavour, a true taste of.
- Keep the original script exactly as printed, including accents.
- Return at most 60 dishes. If the menu is longer, return the 60 most
  representative and set menu.truncated = true.

Field guide:
- menu.language: the printed menu's language, written in English (for example, "Greek").
- menu.languageTag: its BCP-47 language tag (for example, "el", "fr", or "ja").
- menu.currency and price.currency: the ISO 4217 code of the printed prices
  ("EUR", "JPY", "XOF" for West African CFA francs), worked out from the
  symbol and the country. Omit it if you cannot tell.
- menu.restaurantName: the restaurant's name exactly as printed, only if it is
  visibly on the photo (a logo, header, footer, website, social handle or
  "Welcome to" line). Never guess or invent one; omit it if it isn't printed.
- id: a short lowercase ascii slug of the readable name, unique on this menu.
- readableName: how a traveller would say the dish; transliterate non-Latin
  scripts (Kokoretsi, Tonkotsu ramen, Mansaf).
- section: the menu heading the dish sits under, explained, if there is one.
- emoji: one food emoji that best pictures what arrives (🐙 for grilled
  octopus, 🥣 for a yoghurt dip). A single emoji, never text.
- price.amount: a plain number (12,50 becomes 12.5). Omit price if none is printed.
  Read separators the way the menu's country does: a dot or space between
  groups of three digits is a thousands separator, so 6.000 F and 6 000 F
  are both 6000. When a dish shows more than one price (sizes, half and
  full, 6.000/7000 F), use the lowest for amount and keep them all in
  price.asPrinted.
- adventureLevel 1-5: 1 is familiar anywhere (grilled chicken, chips);
  5 is challenging for most visitors (offal, raw, unusual textures).
- effortLevel 1-5: 1 is assembled in minutes; 5 takes hours or days of work.
- flags.spicy 0-3. flags.localSpecialty: hard to find outside this region.
- flags.pork, flags.alcohol, flags.shellfish, flags.meatWithDairy: diners
  rely on these to keep halal or kosher, so when in doubt, say true.
  pork includes lard, gelatine and pork stock. alcohol includes wine,
  beer or spirits used in cooking, sauces or desserts. shellfish covers
  prawns, crab, lobster, mussels, clams, oysters, squid and octopus.
  meatWithDairy is true when meat or poultry is cooked or served with
  milk, cream, butter, cheese or yoghurt.
- confidence 0-1: how sure you are of the explanation, not of the reading.
- wikiTitle: the exact title of the English Wikipedia article about this
  dish ("Moussaka", "Phat kaphrao"), only when you are sure such an article
  exists and is about this dish itself. Omit it for house specials, vague
  items ("chef's salad") and anything you are unsure of. Never guess.
- nutrition: a rough estimate for one plate as this dish is usually served
  here (kcal, and grams of protein, carbs and fat), rounded to whole numbers.
  Base it on a typical restaurant portion, not a recipe book. Omit it for
  drinks, and whenever you cannot picture the portion.
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
    alcohol: bool, shellfish: bool, meatWithDairy: bool,
  },
  required: [
    "spicy", "raw", "offal", "pork", "vegetarian", "vegan", "large", "shareable", "localSpecialty",
    "alcohol", "shellfish", "meatWithDairy",
  ],
  propertyOrdering: [
    "spicy", "raw", "offal", "pork", "vegetarian", "vegan", "large", "shareable", "localSpecialty",
    "alcohol", "shellfish", "meatWithDairy",
  ],
};

const dish = {
  type: "OBJECT",
  properties: {
    id: str,
    originalName: str,
    readableName: str,
    emoji: str,
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
    wikiTitle: str,
    nutrition: {
      type: "OBJECT",
      properties: { kcal: int, proteinG: int, carbsG: int, fatG: int },
      required: ["kcal", "proteinG", "carbsG", "fatG"],
      propertyOrdering: ["kcal", "proteinG", "carbsG", "fatG"],
    },
  },
  required: [
    "id", "originalName", "readableName", "whatItIs", "ingredients", "howItIsMade", "pitch",
    "flags", "allergens", "adventureLevel", "effortLevel", "confidence",
  ],
  // Names first: the app shows each dish the moment its object closes, so order barely matters
  // for speed, but it keeps partial output useful if the stream is cut.
  propertyOrdering: [
    "id", "originalName", "readableName", "emoji", "section", "whatItIs", "ingredients", "howItIsMade",
    "pitch", "price", "flags", "allergens", "adventureLevel", "effortLevel", "confidence", "wikiTitle", "nutrition",
  ],
};

/** Gemini `responseSchema` (OpenAPI subset). Menu metadata comes first so the context strip renders early. */
export const RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    menu: {
      type: "OBJECT",
      properties: { language: str, languageTag: str, currency: str, venueType: str, restaurantName: str, truncated: bool, confidence: num },
      required: ["language", "languageTag", "truncated", "confidence"],
      propertyOrdering: ["language", "languageTag", "currency", "venueType", "restaurantName", "truncated", "confidence"],
    },
    dishes: { type: "ARRAY", items: dish },
  },
  required: ["menu", "dishes"],
  propertyOrdering: ["menu", "dishes"],
};

/**
 * A dense page of 60 dishes takes about 8,000 tokens, so the old 8,000 cap cut real pages off
 * mid-dish (finishReason MAX_TOKENS). 16,000 fits the densest page with room to spare, and still
 * stops a runaway response from turning into a surprising bill.
 */
export const MAX_OUTPUT_TOKENS = 16000;
