/**
 * A photo of a dish from Wikimedia Commons, found through the dish's English Wikipedia article.
 *
 * Only openly licensed photos are returned, always with their credit: images hosted on English
 * Wikipedia itself (not Commons) are usually non-free "fair use" uploads and are refused. The
 * article must describe a food or a drink, so a wrong title can never put a town or a person on
 * a menu.
 */

export interface DishPhoto {
  /** A phone-sized rendition, about [PHOTO_WIDTH] px wide, on upload.wikimedia.org. */
  url: string;
  width: number;
  height: number;
  /** The photographer, as Commons credits them, plain text. */
  credit: string;
  /** "CC BY-SA 4.0", "Public domain"… */
  license: string;
  /** The photo's page on Commons, where the full credit and licence live. */
  source: string;
}

export const PHOTO_WIDTH = 800;

/** Wikimedia asks every client to say who it is and how to reach them. */
export const WIKIMEDIA_HEADERS = {
  "User-Agent": "MenuLango/1.0 (https://menulango-proxy.menulango.workers.dev/privacy; menulango@gmail.com)",
  Accept: "application/json",
};

/** Words that mark a Wikipedia short description as being about something you eat or drink. */
const FOOD_WORDS = [
  "dish", "food", "soup", "stew", "dessert", "pastry", "bread", "cake", "cheese", "sauce", "salad",
  "drink", "beverage", "cocktail", "wine", "beer", "liqueur", "spirit", "tea", "coffee", "noodle",
  "dumpling", "sandwich", "snack", "sweet", "confection", "porridge", "curry", "kebab", "sausage",
  "pie", "pasta", "rice", "meze", "appetizer", "appetiser", "condiment", "dip", "spread", "pudding",
  "cookie", "biscuit", "meat", "fish", "seafood", "stir-fry", "stir fry", "casserole", "flatbread",
  "pancake", "omelette", "cuisine", "delicacy", "street food", "breakfast", "fritter", "skewer",
];

/** Whole words only (plurals allowed): "team" must not pass for "tea", nor "price" for "rice". */
const FOOD_PATTERN = new RegExp(
  `\\b(${FOOD_WORDS.map((w) => {
    const spaced = w.replace(/[-\s]/g, "[-\\s]");
    // "pastry" → "pastries", "curry" → "curries"; others take s or es.
    return spaced.endsWith("y") ? `${spaced.slice(0, -1)}(?:y|ies)` : `${spaced}(?:e?s)?`;
  }).join("|")})\\b`,
  "i",
);

export function looksLikeFood(description: string | undefined): boolean {
  if (!description) return false;
  return FOOD_PATTERN.test(description);
}

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;

/**
 * Looks the dish up and returns its photo, or null when there is no suitable one: no article, not
 * food, no image, or an image without a free licence.
 */
export async function findDishPhoto(title: string, get: Fetch = fetch): Promise<DishPhoto | null> {
  const summary = await get(
    `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(title.replace(/ /g, "_"))}?redirect=true`,
    { headers: WIKIMEDIA_HEADERS },
  );
  if (!summary.ok) return null;
  const page = (await summary.json()) as {
    type?: string;
    description?: string;
    originalimage?: { source: string; width: number; height: number };
  };
  if (page.type !== "standard" || !looksLikeFood(page.description) || !page.originalimage) return null;

  // Commons files live under /wikipedia/commons/; anything else was uploaded to Wikipedia itself,
  // usually under fair use, and can't be reused in an app.
  // The link carries tracking parameters (?utm_source=…): only its path names the file.
  const original = new URL(page.originalimage.source).pathname;
  if (!original.includes("/wikipedia/commons/")) return null;
  const file = decodeURIComponent(original.substring(original.lastIndexOf("/") + 1));

  const info = await get(
    "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo" +
      `&iiprop=url|extmetadata|size&iiurlwidth=${PHOTO_WIDTH}&titles=${encodeURIComponent(`File:${file}`)}`,
    { headers: WIKIMEDIA_HEADERS },
  );
  if (!info.ok) return null;
  const data = (await info.json()) as {
    query?: {
      pages?: Record<
        string,
        {
          imageinfo?: Array<{
            thumburl?: string;
            thumbwidth?: number;
            thumbheight?: number;
            descriptionurl?: string;
            extmetadata?: Record<string, { value?: string }>;
          }>;
        }
      >;
    };
  };
  const image = Object.values(data.query?.pages ?? {})[0]?.imageinfo?.[0];
  const meta = image?.extmetadata ?? {};
  const license = plain(meta.LicenseShortName?.value);
  if (!image?.thumburl || !image.descriptionurl || !license || meta.NonFree?.value === "true") return null;

  return {
    url: image.thumburl,
    width: image.thumbwidth ?? PHOTO_WIDTH,
    height: image.thumbheight ?? PHOTO_WIDTH,
    credit: plain(meta.Artist?.value) || "Wikimedia Commons",
    license,
    source: image.descriptionurl,
  };
}

/** Commons metadata is HTML; the app shows it as one line of plain text. */
export function plain(html: string | undefined): string {
  if (!html) return "";
  return html
    .replace(/<[^>]*>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120);
}
