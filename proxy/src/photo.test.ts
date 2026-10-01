import { test } from "node:test";
import assert from "node:assert/strict";
import { findDishPhoto, looksLikeFood, plain } from "./photo.ts";

// As Wikipedia sends it today: with tracking parameters on the end.
const COMMONS_ORIGINAL =
  "https://upload.wikimedia.org/wikipedia/commons/a/ab/Moussaka_dish.jpg?utm_source=en.wikipedia.org&utm_campaign=api";

function fakeWikimedia(summary: object, imageinfo: object | null) {
  return async (url: string) => {
    if (url.startsWith("https://en.wikipedia.org/api/rest_v1/page/summary/")) {
      return new Response(JSON.stringify(summary), { status: 200 });
    }
    if (url.startsWith("https://commons.wikimedia.org/w/api.php")) {
      // The file must be named without the tracking parameters, or Commons won't find it.
      if (!url.includes(encodeURIComponent("File:Moussaka_dish.jpg")) || url.includes("utm_")) {
        return new Response(JSON.stringify({ query: { pages: {} } }), { status: 200 });
      }
      const pages = imageinfo ? { "123": { imageinfo: [imageinfo] } } : {};
      return new Response(JSON.stringify({ query: { pages } }), { status: 200 });
    }
    return new Response("", { status: 404 });
  };
}

const goodSummary = {
  type: "standard",
  description: "Eggplant- or potato-based dish",
  originalimage: { source: COMMONS_ORIGINAL, width: 4000, height: 3000 },
};
const goodInfo = {
  thumburl: "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Moussaka_dish.jpg/800px-Moussaka_dish.jpg",
  thumbwidth: 800,
  thumbheight: 600,
  descriptionurl: "https://commons.wikimedia.org/wiki/File:Moussaka_dish.jpg",
  extmetadata: {
    Artist: { value: '<a href="//commons.wikimedia.org/wiki/User:Jane">Jane Doe</a>' },
    LicenseShortName: { value: "CC BY-SA 4.0" },
  },
};

test("returns a freely licensed Commons photo with its credit", async () => {
  const photo = await findDishPhoto("Moussaka", fakeWikimedia(goodSummary, goodInfo));
  assert.deepEqual(photo, {
    url: goodInfo.thumburl,
    width: 800,
    height: 600,
    credit: "Jane Doe",
    license: "CC BY-SA 4.0",
    source: goodInfo.descriptionurl,
  });
});

test("refuses articles that aren't about food", async () => {
  const town = { ...goodSummary, description: "Town in Attica, Greece" };
  assert.equal(await findDishPhoto("Marathon", fakeWikimedia(town, goodInfo)), null);
});

test("refuses disambiguation pages and articles without an image", async () => {
  assert.equal(await findDishPhoto("Pie", fakeWikimedia({ ...goodSummary, type: "disambiguation" }, goodInfo)), null);
  const noImage = { type: "standard", description: "Greek dish" };
  assert.equal(await findDishPhoto("Kokoretsi", fakeWikimedia(noImage, goodInfo)), null);
});

test("refuses images hosted on Wikipedia itself, which are usually non-free", async () => {
  const fairUse = {
    ...goodSummary,
    originalimage: { source: "https://upload.wikimedia.org/wikipedia/en/1/12/Logo.png", width: 300, height: 300 },
  };
  assert.equal(await findDishPhoto("Big Mac", fakeWikimedia(fairUse, goodInfo)), null);
});

test("refuses files without a licence or marked non-free", async () => {
  const unlicensed = { ...goodInfo, extmetadata: { Artist: { value: "Someone" } } };
  assert.equal(await findDishPhoto("Moussaka", fakeWikimedia(goodSummary, unlicensed)), null);
  const nonFree = {
    ...goodInfo,
    extmetadata: { ...goodInfo.extmetadata, NonFree: { value: "true" } },
  };
  assert.equal(await findDishPhoto("Moussaka", fakeWikimedia(goodSummary, nonFree)), null);
});

test("returns null when Wikipedia has no such article", async () => {
  const missing = async () => new Response("", { status: 404 });
  assert.equal(await findDishPhoto("Not a real dish", missing), null);
});

test("recognises food descriptions and cleans credits", () => {
  assert.ok(looksLikeFood("Thai stir-fried noodle dish"));
  assert.ok(looksLikeFood("Japanese noodle soup"));
  assert.ok(looksLikeFood("Greek dessert made of semolina custard"));
  assert.ok(looksLikeFood("Fried dough pastries"));
  assert.ok(!looksLikeFood("American actor"));
  assert.ok(!looksLikeFood("American basketball team"));
  assert.ok(!looksLikeFood("Retail price index"));
  assert.ok(!looksLikeFood(undefined));
  assert.equal(plain("<span>Jean &amp; Paul</span>\n  <i>Studio</i>"), "Jean & Paul Studio");
});
