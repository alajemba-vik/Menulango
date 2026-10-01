/**
 * The privacy policy, served by the proxy so the app, Google Play and the App Store all link to
 * one URL that is always in sync with what the proxy actually does.
 */
export const PRIVACY_HTML = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>MenuLango privacy policy</title>
<style>
  body{font:17px/1.6 Georgia,serif;color:#1A1D17;background:#FAF8F3;max-width:640px;margin:0 auto;padding:40px 20px}
  h1{font-size:30px;line-height:1.2} h2{font:600 12px/1.4 system-ui,sans-serif;letter-spacing:.12em;text-transform:uppercase;color:#8E9284;margin-top:32px}
  @media (prefers-color-scheme:dark){body{color:#EFEBE0;background:#14130E}h2{color:#78755F}}
</style></head><body>
<h1>MenuLango privacy policy</h1>
<p>Last updated 29 September 2026.</p>
<h2>What we collect</h2>
<p>There are no accounts. We do not ask for your name, email address, location or dietary preferences.</p>
<p>The app creates a random identifier on install. It is sent with a menu photo only to rate-limit abuse. It is not linked to you, device hardware, advertising or tracking.</p>
<p>To stop others from misusing our server, each scan also carries a short-lived token from Firebase App Check, using Apple App Attest or Google Play Integrity. It only proves the request comes from the real MenuLango app on a genuine device. It contains no personal information and is not used for tracking.</p>
<h2>What happens to your photo</h2>
<p>When you scan a menu, its photo is sent to our server, which runs on Cloudflare, and to Google's Gemini API, only to read and explain that menu. Our server does not store the photo. Google may keep it for a limited time to detect abuse and does not use it to train its models.</p>
<p>When you open a dish, the app may show a photo of how it is typically served. Our server looks it up on Wikipedia and Wikimedia Commons by the dish's name and passes the image on, so your phone never contacts Wikimedia itself. Photos are shown with their author and licence.</p>
<p>If you use "Share with your table" (part of MenuLango Plus), the menu's explanations are stored on our server for 24 hours under a random link, so the people you give it to can open it. Anyone with the link can read it during that time. Your photo, your picks and anything about you are never included. After 24 hours it is deleted automatically.</p>
<p>MenuLango never uses your location. Where a menu seems to come from is worked out from the menu itself.</p>
<p>Your scanned menus, their photos and the choices you make are stored on your device so you can reopen them offline. Deleting the app deletes them.</p>
<h2>Translations and backups</h2>
<p>Waiter-note translation happens on your device. Your dietary preferences, diners, picks, quantities and notes stay on your device.</p>
<p>You can create an encrypted backup file protected by a passphrase you choose. It stays with you: you choose where to share or store it, and we never receive the file or its passphrase.</p>
<h2>Purchases</h2>
<p>Purchases are processed by Apple or Google and managed through RevenueCat. We do not see your payment details.</p>
<h2>No tracking</h2>
<p>MenuLango contains no advertising, no analytics SDK and no third-party tracking.</p>
<h2>Allergens</h2>
<p>Allergen information is an estimate of what a dish usually contains. It is never a guarantee. Always ask the restaurant if you have an allergy.</p>
<h2>Contact</h2>
<p>Questions about this policy: <a href="mailto:menulango@gmail.com?subject=MenuLango%20privacy">menulango@gmail.com</a>.</p>
</body></html>`;
