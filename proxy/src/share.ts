/**
 * Menus shared with a table: one diner scans, everyone else opens a link (or scans a QR code) and
 * reads the same explained menu in their browser, with no app. Stored for [SHARE_TTL_S] under an
 * unguessable id, then deleted by Cloudflare. Only what the page shows is kept: no photo, no
 * picks, nothing about the person who shared it.
 */

/** One meal's worth: long enough for the table, short enough that nothing lingers. */
export const SHARE_TTL_S = 24 * 60 * 60;
export const MAX_SHARE_BYTES = 400_000;
const MAX_DISHES = 200;

export interface SharedDish {
  name: string;
  original: string;
  section?: string;
  emoji?: string;
  price?: string;
  whatItIs?: string;
  pitch?: string;
  ingredients: string[];
  tags: Array<"vegetarian" | "vegan" | "spicy" | "raw" | "pork" | "offal" | "local">;
  likelyContains: string[];
  mayContain: string[];
}

export interface SharedMenu {
  title: string;
  /** The language the explanations are written in, which the page's labels follow. */
  locale: string;
  dishes: SharedDish[];
  createdAt: number;
}

const LOCALE = /^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/;

/** Reads what the app sent, keeping only known fields, as plain text, within fixed limits. */
export function parseShare(body: unknown, now: number): SharedMenu | null {
  if (typeof body !== "object" || body === null) return null;
  const b = body as Record<string, unknown>;
  const menu = b.menu as Record<string, unknown> | undefined;
  const rawDishes = Array.isArray(menu?.dishes) ? (menu!.dishes as unknown[]) : [];
  const dishes = rawDishes.slice(0, MAX_DISHES).map(toDish).filter((d): d is SharedDish => d !== null);
  if (dishes.length === 0) return null;
  const locale = typeof b.locale === "string" && LOCALE.test(b.locale) ? b.locale : "en";
  return { title: text(b.title, 120) ?? "Menu", locale, dishes, createdAt: now };
}

function toDish(raw: unknown): SharedDish | null {
  if (typeof raw !== "object" || raw === null) return null;
  const d = raw as Record<string, unknown>;
  const name = text(d.readableName, 120);
  if (!name) return null;
  const flags = (d.flags ?? {}) as Record<string, unknown>;
  const allergens = (d.allergens ?? {}) as Record<string, unknown>;
  const price = (d.price ?? {}) as Record<string, unknown>;
  const tags: SharedDish["tags"] = [];
  if (flags.vegan === true) tags.push("vegan");
  else if (flags.vegetarian === true) tags.push("vegetarian");
  if (typeof flags.spicy === "number" && flags.spicy >= 2) tags.push("spicy");
  if (flags.raw === true) tags.push("raw");
  if (flags.pork === true) tags.push("pork");
  if (flags.offal === true) tags.push("offal");
  if (flags.localSpecialty === true) tags.push("local");
  return {
    name,
    original: text(d.originalName, 160) ?? name,
    section: text(d.section, 80),
    emoji: text(d.emoji, 16),
    price: text(price.asPrinted, 40),
    whatItIs: text(d.whatItIs, 600),
    pitch: text(d.pitch, 240),
    ingredients: list(d.ingredients, 20, 60),
    tags,
    likelyContains: list(allergens.likelyContains, 14, 40),
    mayContain: list(allergens.mayContain, 14, 40),
  };
}

function text(value: unknown, max: number): string | undefined {
  if (typeof value !== "string") return undefined;
  const clean = value.replace(/\s+/g, " ").trim();
  return clean ? clean.slice(0, max) : undefined;
}

function list(value: unknown, maxItems: number, maxLength: number): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((v) => text(v, maxLength))
    .filter((v): v is string => !!v)
    .slice(0, maxItems);
}

/** 12 characters of [A-Za-z0-9]: about 71 bits, so links can't be guessed or counted through. */
export function newShareId(): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  const bytes = crypto.getRandomValues(new Uint8Array(12));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}

export const SHARE_ID = /^[A-Za-z0-9]{12}$/;

// ---- The page ----

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/** A self-contained page: no scripts, no fonts or images fetched from anywhere. */
export function renderSharePage(menu: SharedMenu): string {
  const l = labels(menu.locale);
  const e = escapeHtml;
  const lang = e(menu.locale.split("-")[0]);
  const rtl = ["ar", "he", "fa", "ur"].includes(menu.locale.split("-")[0].toLowerCase());
  const sections: Array<{ name?: string; dishes: SharedDish[] }> = [];
  for (const dish of menu.dishes) {
    const last = sections[sections.length - 1];
    if (last && last.name === dish.section) last.dishes.push(dish);
    else sections.push({ name: dish.section, dishes: [dish] });
  }
  const body = sections
    .map(
      (s) =>
        `${s.name ? `<h2>${e(s.name)}</h2>` : ""}${s.dishes.map((d) => renderDish(d, l)).join("")}`,
    )
    .join("");
  const description = l.dishCount.replace("%d", String(menu.dishes.length));
  return `<!doctype html>
<html lang="${lang}"${rtl ? ' dir="rtl"' : ""}><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex,nofollow">
<meta name="color-scheme" content="light dark">
<meta name="theme-color" content="#6B2E83">
<title>${e(menu.title)} · MenuLango</title>
<meta property="og:title" content="${e(menu.title)}">
<meta property="og:description" content="${e(description)} · MenuLango">
<meta property="og:site_name" content="MenuLango">
<style>
:root{--paper:#FAF8FC;--raised:#FFFFFF;--sunk:#F2EEF6;--ink:#1E1624;--muted:#564B5E;--faint:#736A7A;--rule:#E9E3EF;--seal:#6B2E83;--wash:#EFE6F4;--alarm:#8E3037;--alarm-wash:#F7ECEE}
@media (prefers-color-scheme:dark){:root{--paper:#151118;--raised:#211A26;--sunk:#2A2230;--ink:#F3EEF6;--muted:#B3A8BB;--faint:#92889A;--rule:#352C3B;--seal:#C9A2E3;--wash:#2E2338;--alarm:#E08790;--alarm-wash:#2D1C21}}
*{box-sizing:border-box}
body{margin:0;background:var(--paper);color:var(--ink);font:17px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,"Noto Sans",sans-serif;-webkit-text-size-adjust:100%}
main{max-width:640px;margin:0 auto;padding:max(24px,env(safe-area-inset-top)) 16px 40px}
header{padding:8px 0 20px}
.brand{font-size:13px;font-weight:600;letter-spacing:.12em;text-transform:uppercase;color:var(--seal)}
h1{font-size:32px;line-height:1.15;margin:6px 0 4px;letter-spacing:-.01em}
.sub{color:var(--muted);font-size:15px;margin:0}
h2{font-size:13px;font-weight:600;letter-spacing:.12em;text-transform:uppercase;color:var(--faint);margin:28px 4px 10px}
.dish{background:var(--raised);border:1px solid var(--rule);border-radius:18px;padding:16px 18px;margin:0 0 12px}
.top{display:flex;gap:12px;align-items:flex-start}
.plate{font-size:30px;line-height:1;flex:none;width:44px;height:44px;display:grid;place-items:center;background:var(--sunk);border-radius:12px}
.names{flex:1;min-width:0}
h3{font-size:19px;line-height:1.25;margin:0}
.orig{color:var(--muted);font-size:14px;margin:2px 0 0}
.price{flex:none;font-weight:600;font-variant-numeric:tabular-nums;white-space:nowrap}
.pitch{color:var(--muted);font-size:15px;margin:10px 0 0}
.what{margin:8px 0 0}
.tags{display:flex;flex-wrap:wrap;gap:6px;margin:10px 0 0;padding:0;list-style:none}
.tags li{background:var(--wash);color:var(--seal);font-size:13px;font-weight:600;padding:3px 10px;border-radius:999px}
.ing{color:var(--muted);font-size:14px;margin:8px 0 0}
.allergy{background:var(--alarm-wash);color:var(--ink);font-size:14px;border-radius:12px;padding:8px 12px;margin:10px 0 0}
.allergy b{color:var(--alarm)}
.note{background:var(--sunk);color:var(--muted);font-size:14px;border-radius:14px;padding:12px 14px;margin:20px 0 0}
footer{color:var(--faint);font-size:13px;text-align:center;margin:28px 0 0}
footer b{color:var(--seal)}
</style></head><body><main>
<header><div class="brand">MenuLango</div><h1>${e(menu.title)}</h1><p class="sub">${e(description)}</p></header>
${body}
<p class="note">${e(l.aiNote)} <b>${e(l.askAllergy)}</b></p>
<footer><b>${e(l.explainedBy)}</b><br>${e(l.expires)}</footer>
</main></body></html>`;
}

function renderDish(d: SharedDish, l: Labels): string {
  const e = escapeHtml;
  const originalLine = d.original !== d.name ? `<p class="orig" lang="">${e(d.original)}</p>` : "";
  const tags = d.tags.length ? `<ul class="tags">${d.tags.map((t) => `<li>${e(l.tags[t])}</li>`).join("")}</ul>` : "";
  const allergies = [
    d.likelyContains.length ? `${e(l.often)} ${e(d.likelyContains.join(", "))}.` : "",
    d.mayContain.length ? `${e(l.may)} ${e(d.mayContain.join(", "))}.` : "",
  ]
    .filter(Boolean)
    .join(" ");
  return `<article class="dish">
<div class="top"><div class="plate" aria-hidden="true">${e(d.emoji ?? "🍽️")}</div><div class="names"><h3>${e(d.name)}</h3>${originalLine}</div>${d.price ? `<div class="price">${e(d.price)}</div>` : ""}</div>
${d.pitch ? `<p class="pitch">${e(d.pitch)}</p>` : ""}
${d.whatItIs ? `<p class="what">${e(d.whatItIs)}</p>` : ""}
${tags}
${d.ingredients.length ? `<p class="ing">${e(d.ingredients.join(" · "))}</p>` : ""}
${allergies ? `<p class="allergy">${allergies}</p>` : ""}
</article>`;
}

/** The page for a link that has expired or never existed. */
export function renderGonePage(locale: string): string {
  const l = labels(locale);
  const e = escapeHtml;
  return `<!doctype html><html lang="${e(locale.split("-")[0])}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
<meta name="color-scheme" content="light dark"><title>MenuLango</title>
<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#FAF8FC;color:#1E1624;font:17px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;text-align:center;padding:24px}
@media (prefers-color-scheme:dark){body{background:#151118;color:#F3EEF6}}b{color:#6B2E83}@media (prefers-color-scheme:dark){b{color:#C9A2E3}}</style></head>
<body><div><b>MenuLango</b><p>${e(l.gone)}</p></div></body></html>`;
}

// ---- Labels, in the nine languages the app speaks ----

interface Labels {
  dishCount: string;
  tags: Record<SharedDish["tags"][number], string>;
  often: string;
  may: string;
  aiNote: string;
  askAllergy: string;
  explainedBy: string;
  expires: string;
  gone: string;
}

const LABELS: Record<string, Labels> = {
  en: {
    dishCount: "%d dishes, explained",
    tags: { vegetarian: "Vegetarian", vegan: "Vegan", spicy: "Spicy", raw: "Raw", pork: "Pork", offal: "Offal", local: "Local" },
    often: "Often contains",
    may: "May contain",
    aiNote: "These explanations were written by AI from a photo of the menu, so they can be wrong.",
    askAllergy: "Ask the restaurant if you have an allergy.",
    explainedBy: "Explained by MenuLango",
    expires: "This link works for 24 hours from when it was shared.",
    gone: "This shared menu has expired. Ask whoever shared it to send it again.",
  },
  fr: {
    dishCount: "%d plats expliqués",
    tags: { vegetarian: "Végétarien", vegan: "Végan", spicy: "Épicé", raw: "Cru", pork: "Porc", offal: "Abats", local: "Local" },
    often: "Contient souvent",
    may: "Peut contenir",
    aiNote: "Ces explications ont été écrites par une IA à partir d'une photo du menu, elles peuvent donc être fausses.",
    askAllergy: "Demandez au restaurant si vous avez une allergie.",
    explainedBy: "Expliqué par MenuLango",
    expires: "Ce lien fonctionne pendant 24 heures après son partage.",
    gone: "Ce menu partagé a expiré. Demandez à la personne qui l'a partagé de le renvoyer.",
  },
  es: {
    dishCount: "%d platos explicados",
    tags: { vegetarian: "Vegetariano", vegan: "Vegano", spicy: "Picante", raw: "Crudo", pork: "Cerdo", offal: "Casquería", local: "Local" },
    often: "Suele llevar",
    may: "Puede contener",
    aiNote: "Estas explicaciones las escribió una IA a partir de una foto de la carta, así que pueden tener errores.",
    askAllergy: "Pregunta en el restaurante si tienes alguna alergia.",
    explainedBy: "Explicado por MenuLango",
    expires: "Este enlace funciona durante 24 horas desde que se compartió.",
    gone: "Esta carta compartida ha caducado. Pide a quien la compartió que la envíe de nuevo.",
  },
  ar: {
    dishCount: "%d طبقًا مشروحًا",
    tags: { vegetarian: "نباتي", vegan: "نباتي صرف", spicy: "حار", raw: "نيء", pork: "لحم خنزير", offal: "أحشاء", local: "محلي" },
    often: "غالبًا يحتوي على",
    may: "قد يحتوي على",
    aiNote: "كتب الذكاء الاصطناعي هذه الشروح من صورة القائمة، لذا قد تكون خاطئة.",
    askAllergy: "اسأل المطعم إن كانت لديك حساسية.",
    explainedBy: "شرحها MenuLango",
    expires: "يعمل هذا الرابط لمدة 24 ساعة من وقت مشاركته.",
    gone: "انتهت صلاحية هذه القائمة المشتركة. اطلب ممن شاركها إرسالها مجددًا.",
  },
  zh: {
    dishCount: "已讲解 %d 道菜",
    tags: { vegetarian: "素食", vegan: "纯素", spicy: "辣", raw: "生食", pork: "猪肉", offal: "内脏", local: "当地特色" },
    often: "通常含有",
    may: "可能含有",
    aiNote: "这些讲解由 AI 根据菜单照片撰写，可能有误。",
    askAllergy: "如有过敏，请向餐厅确认。",
    explainedBy: "由 MenuLango 讲解",
    expires: "此链接自分享起 24 小时内有效。",
    gone: "此共享菜单已过期。请让分享者重新发送。",
  },
  ja: {
    dishCount: "%d 品を解説",
    tags: { vegetarian: "ベジタリアン", vegan: "ヴィーガン", spicy: "辛い", raw: "生", pork: "豚肉", offal: "内臓", local: "郷土料理" },
    often: "よく含まれるもの:",
    may: "含まれる可能性:",
    aiNote: "この解説はメニューの写真をもとに AI が書いたもので、誤りがあるかもしれません。",
    askAllergy: "アレルギーがある場合はお店に確認してください。",
    explainedBy: "MenuLango による解説",
    expires: "このリンクは共有から24時間有効です。",
    gone: "この共有メニューは期限切れです。共有した人にもう一度送ってもらってください。",
  },
  ko: {
    dishCount: "요리 %d개 설명",
    tags: { vegetarian: "채식", vegan: "비건", spicy: "매움", raw: "날것", pork: "돼지고기", offal: "내장", local: "현지 음식" },
    often: "보통 들어가는 것:",
    may: "들어갈 수 있는 것:",
    aiNote: "이 설명은 메뉴 사진을 바탕으로 AI가 작성해 틀릴 수 있어요.",
    askAllergy: "알레르기가 있다면 식당에 확인하세요.",
    explainedBy: "MenuLango 설명",
    expires: "이 링크는 공유한 때부터 24시간 동안 열 수 있어요.",
    gone: "공유된 메뉴가 만료되었어요. 공유한 사람에게 다시 보내 달라고 하세요.",
  },
  ru: {
    dishCount: "Блюд с объяснениями: %d",
    tags: { vegetarian: "Вегетарианское", vegan: "Веганское", spicy: "Острое", raw: "Сырое", pork: "Свинина", offal: "Субпродукты", local: "Местное" },
    often: "Обычно содержит",
    may: "Может содержать",
    aiNote: "Эти объяснения написал ИИ по фотографии меню, поэтому в них возможны ошибки.",
    askAllergy: "Если у вас аллергия, уточните в ресторане.",
    explainedBy: "Объяснено MenuLango",
    expires: "Ссылка работает 24 часа с момента, когда ею поделились.",
    gone: "Срок действия этого меню истёк. Попросите того, кто им поделился, отправить его снова.",
  },
  hi: {
    dishCount: "%d डिशें समझाई गईं",
    tags: { vegetarian: "शाकाहारी", vegan: "वीगन", spicy: "तीखा", raw: "कच्चा", pork: "पोर्क", offal: "ऑफ़ल", local: "स्थानीय" },
    often: "अक्सर इसमें होता है",
    may: "इसमें हो सकता है",
    aiNote: "ये व्याख्याएँ मेन्यू की फ़ोटो से AI ने लिखी हैं, इसलिए गलत हो सकती हैं।",
    askAllergy: "एलर्जी हो तो रेस्टोरेंट से पूछें।",
    explainedBy: "MenuLango ने समझाया",
    expires: "यह लिंक साझा करने के 24 घंटे तक काम करेगा।",
    gone: "यह साझा मेन्यू समाप्त हो गया है। जिसने भेजा था, उससे फिर भेजने को कहें।",
  },
};

function labels(locale: string): Labels {
  return LABELS[locale.split("-")[0].toLowerCase()] ?? LABELS.en;
}
