# Shipping MenuLango

Everything that needs an account, a password or a payment method. Do it in this order: Android is
submitted first and stands on its own if iOS slips.

## 1. Proxy (15 minutes)

1. Create a Gemini API key in Google AI Studio **on a billing-enabled project** (paid tier: your
   users' photos must not be used for training). Set a monthly budget alert of a few dollars.
2. Deploy:
   ```bash
   cd proxy && npm install
   npx wrangler login
   npx wrangler secret put GEMINI_API_KEY
   npx wrangler deploy
   ```
3. Check it before touching the app:
   ```bash
   curl https://menulango-proxy.<you>.workers.dev/health          # → ok
   sips -Z 1536 -s formatOptions 80 real-menu.jpg --out menu.jpg
   scripts/scan.sh https://menulango-proxy.<you>.workers.dev menu.jpg en-US
   ```
4. Try real Greek, Japanese and Arabic menus. If a script reads badly, tune `src/prompt.ts` and
   redeploy — no app release needed. Only move `GEMINI_MODEL` up from `gemini-2.5-flash-lite` if
   quality is genuinely insufficient; `npx wrangler tail` shows tokens per scan.
5. Your privacy policy URL is `https://menulango-proxy.<you>.workers.dev/privacy`.

## 2. RevenueCat (30 minutes)

1. Create a project with two apps: Google Play (`com.menulango.app`) and App Store
   (`com.menulango.app`).
2. Products, identical ids in both stores:
   | Product id | Play Console | App Store Connect | Price |
   |---|---|---|---|
   | `menulango_trip_week` | Subscription, base plan `P1W`, auto-renewing | Auto-renewable subscription, 1 week | €4.99 / $4.99 |
   | `menulango_annual` | Subscription, base plan `P1Y` | Auto-renewable subscription, 1 year | €29.99 / $29.99 |
   | `menulango_lifetime` (optional) | One-time product | Non-consumable | €49.99 / $49.99 |

   Put both subscriptions in one App Store subscription group so upgrading week → year works.
3. Entitlement **`plus`**, attached to all three products.
4. Offering **`default`** (mark it current) with packages **Weekly** → trip week, **Annual** →
   annual, **Lifetime** → lifetime. The app maps packages by type, so the package types matter.
5. Copy the public SDK keys into `secrets.properties` / `Secrets.xcconfig` (see README).

## 3. Google Play (day 4)

1. Upload key, once, kept out of the repo:
   ```bash
   keytool -genkeypair -v -keystore ~/menulango-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
   Then in `secrets.properties`:
   ```properties
   MENULANGO_KEYSTORE=/Users/<you>/menulango-upload.jks
   MENULANGO_KEYSTORE_PASSWORD=…
   MENULANGO_KEY_ALIAS=upload
   MENULANGO_KEY_PASSWORD=…
   ```
2. `./gradlew :androidApp:bundleRelease` → `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
   Bump `versionCode` in `androidApp/build.gradle.kts` for every upload.
3. Play Console → create app → upload to **Internal testing** first, install it from the Play
   link, buy the Trip Pass with a licence-tester account, then promote to **Production**.
4. App content:
   - Privacy policy: the proxy `/privacy` URL.
   - Data safety: *Photos* collected, sent off-device, not shared with third parties for their own
     use, not stored, required for the app to work; *App activity: purchase history* via RevenueCat.
     No location, no identifiers for advertising. Data encrypted in transit: yes.
   - Ads: none. Target audience: 18+ (it is a restaurant app with no child-directed content).
   - Countries: include the United States.
5. Promo code for the judges: Play Console → Monetize → Promo codes (works for subscriptions with
   a free-trial-style redemption) — or add the judges as licence testers.

## 4. App Store (day 5)

1. In `Secrets.xcconfig` set `TEAM_ID`. Open `iosApp/iosApp.xcodeproj`, select a real iPhone, Run,
   and check: camera permission prompt, safe areas, the back swipe, and the dish sheet with
   Dynamic Type at its largest.
2. Product → Archive → Distribute → App Store Connect.
3. App Store Connect: privacy policy URL; App Privacy → *Photos* (App Functionality, not linked to
   the user, not used for tracking) and *Purchase History* (App Functionality). The camera usage
   text is already in the project: it says what the photo is for and that it isn't kept.
4. Attach the subscriptions to the version, add the review notes below, submit.
5. Promo codes: App Store Connect → the app → Offer codes (subscriptions) — generate a few one-time
   codes for the judges.

**Review notes (paste):** *MenuLango explains restaurant menus from a photo. To try it without a
menu, photograph any printed menu or choose a menu photo from the library. Three scans a month are
free; the choosing modes ("Help me choose") are part of the MenuLango Plus subscription.*

## 5. Store listing copy

**Name:** MenuLango — Menu Translator
**Subtitle / short description:** Every dish explained. Order bravely.

**Description:**
> Twenty dishes on the menu, and you recognise three. MenuLango reads the whole menu from one photo
> and explains every dish — what arrives on the plate, what's in it, and how it's cooked: the
> octopus simmered for an hour then charred over coals, the lamb sealed in paper for five hours.
>
> Then it helps you choose. Something new. Something special. Something you can only eat here.
> Or, on a tired evening, something simple.
>
> • Reads menus in any language and script, including Greek, Japanese and Arabic
> • Explains dishes rather than translating them word for word
> • "Check before you order" on every dish — and always: ask the restaurant if you have an allergy
> • Menus you've read open instantly, even offline
> • No account, no ads, no tracking
>
> Three menus a month are free, fully explained. MenuLango Plus adds unlimited menus, the four ways
> to choose, and a memory of what you've tried. The 7-day Trip Pass is made for one holiday.

**Keywords (App Store):** menu,translator,food,restaurant,travel,dish,cuisine,camera,allergens,greek

**Screenshots:** debug builds can show the sample menu; set the device to light mode, open the
sample, and capture Menu → Dish → Choose → Paywall. The 1024×1024 icon is in
`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`.
