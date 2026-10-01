import { test } from "node:test";
import assert from "node:assert/strict";
import { escapeHtml, newShareId, parseShare, renderGonePage, renderSharePage, SHARE_ID } from "./share.ts";

const dish = (over: object = {}) => ({
  readableName: "Moussaka",
  originalName: "Μουσακάς",
  section: "Mains",
  emoji: "🍆",
  price: { amount: 12, asPrinted: "12,00€" },
  whatItIs: "Layers of aubergine and spiced meat under a thick béchamel.",
  pitch: "The one everyone should try once.",
  ingredients: ["aubergine", "minced lamb", "béchamel"],
  flags: { vegetarian: false, vegan: false, spicy: 0, raw: false, pork: false, offal: false, localSpecialty: true },
  allergens: { likelyContains: ["milk", "gluten"], mayContain: ["egg"] },
  ...over,
});

test("keeps only the fields the page shows, as plain text", () => {
  const menu = parseShare({ title: "Greek taverna", locale: "en-GB", menu: { dishes: [dish({ secret: "x" })] } }, 1);
  assert.ok(menu);
  assert.equal(menu.title, "Greek taverna");
  assert.equal(menu.locale, "en-GB");
  assert.deepEqual(menu.dishes[0].tags, ["local"]);
  assert.equal(menu.dishes[0].price, "12,00€");
  assert.ok(!("secret" in menu.dishes[0]));
});

test("refuses anything that isn't a menu with dishes", () => {
  assert.equal(parseShare(null, 1), null);
  assert.equal(parseShare({ menu: { dishes: [] } }, 1), null);
  assert.equal(parseShare({ menu: { dishes: [{ originalName: "no readable name" }] } }, 1), null);
});

test("caps long text and odd locales", () => {
  const menu = parseShare({ title: "x".repeat(500), locale: "<script>", menu: { dishes: [dish({ whatItIs: "y".repeat(5000) })] } }, 1);
  assert.ok(menu);
  assert.equal(menu.title.length, 120);
  assert.equal(menu.locale, "en");
  assert.equal(menu.dishes[0].whatItIs?.length, 600);
});

test("the page escapes everything that came from the menu", () => {
  const menu = parseShare(
    { title: "<img src=x onerror=alert(1)>", locale: "en", menu: { dishes: [dish({ readableName: "<script>alert(1)</script>" })] } },
    1,
  )!;
  const page = renderSharePage(menu);
  assert.ok(!page.includes("<script>alert"));
  assert.ok(!page.includes("<img src=x"));
  assert.ok(page.includes("&lt;script&gt;"));
  assert.ok(page.includes('name="robots" content="noindex,nofollow"'));
});

test("the page speaks the sharer's language and says how long it lasts", () => {
  const fr = renderSharePage(parseShare({ title: "Taverne", locale: "fr-FR", menu: { dishes: [dish()] } }, 1)!);
  assert.ok(fr.includes("Peut contenir"));
  assert.ok(fr.includes("24 heures"));
  const ar = renderSharePage(parseShare({ title: "مطعم", locale: "ar", menu: { dishes: [dish()] } }, 1)!);
  assert.ok(ar.includes('dir="rtl"'));
  assert.ok(renderGonePage("en").includes("expired"));
});

test("ids are 12 random letters and digits", () => {
  const ids = new Set(Array.from({ length: 200 }, newShareId));
  assert.equal(ids.size, 200);
  for (const id of ids) assert.ok(SHARE_ID.test(id));
  assert.equal(escapeHtml(`"'<>&`), "&quot;&#39;&lt;&gt;&amp;");
});
