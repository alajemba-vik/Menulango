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
<p>Last updated 23 September 2026.</p>
<h2>What we collect</h2>
<p>When you photograph a menu, the photo is sent to our server so an AI model can read it. We do not ask for your name, email address or location, and there are no accounts.</p>
<p>The app creates a random identifier on install. It is sent with each photo so we can stop any one device from overloading the service. It is not linked to you, your device hardware, or advertising.</p>
<h2>What happens to your photo</h2>
<p>Your photo is passed to Google's Gemini API on a paid plan, whose terms do not allow your data to be used to train models. Our server does not store photos. Google may keep request data for a limited period for abuse monitoring, as described in its terms.</p>
<p>The menus you scan, and the photos you took of them, are kept on your phone so you can reopen them offline. Deleting the app deletes them.</p>
<h2>Purchases</h2>
<p>Purchases are processed by Apple or Google and managed through RevenueCat, which receives an anonymous app user ID and your purchase history so your subscription works across reinstalls. We never see your payment details.</p>
<h2>No tracking</h2>
<p>MenuLango contains no advertising, no analytics SDK and no third-party tracking.</p>
<h2>Allergens</h2>
<p>Allergen information is an estimate of what a dish usually contains. It is never a guarantee. Always ask the restaurant if you have an allergy.</p>
<h2>Contact</h2>
<p>Questions about this policy: open an issue on the project's public repository.</p>
</body></html>`;
