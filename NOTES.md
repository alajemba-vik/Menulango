# Release notes and verification

## Shipped on `wip-translation`

- Waiter notes translate on-device into the menu language when the diner chooses **Show the waiter**.
  Results are cached with the order; **For me** and **For the restaurant** never make a network call.
  When an offline ML Kit model is unavailable, the diner keeps the original note and sees a clear
  “Say or show this note” fallback.
- Opening a menu prefetches its restaurant-language translation model on Wi-Fi in the background.
- Settings can create and restore a user-held encrypted backup. It uses a fresh salt, PBKDF2-SHA256
  (600,000 iterations) and AES-GCM; the app never stores a passphrase. A restore merges preferences,
  saved menus, eating history and per-menu diners, quantities and notes.
- Android uses the native share sheet and document picker. iOS uses `UIActivityViewController` and
  `UIDocumentPickerViewController`, so the user can choose their own installed storage provider.
- There is no MenuLango account or server copy of the diner's data. (Firebase is used only for App Check, which proves scans come from the real app; it holds no user data.)
- **Restore purchases** is always available in Settings. Purchases remain tied to Apple or Google
  through RevenueCat, with clear restored, nothing-to-restore and failure messages.
- **Something wrong?** opens a prefilled email to `menulango@gmail.com` containing only app and OS
  version details, never menu or personal data.
- The deployed privacy page now matches the product: scan photos are not stored on the server;
  translations and backups are device-side; backups are encrypted and user-held; no ads, analytics
  or accounts.

## Deliberately not changed

- No onboarding or coach-mark UI, visual tokens, app icon, Help me choose, scroll behavior or
  texture code was changed.
- No version bump, store upload, push or merge to `main` was performed.
- Direct cloud-drive integrations were not added. Native sharing lets the user choose iCloud Drive,
  Google Drive, Dropbox or another installed provider without MenuLango storing credentials.

## Verification

- `./gradlew spotlessApply && ./gradlew spotlessCheck`
- `./gradlew :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test :androidApp:assembleDebug`
- `cd proxy && npm test && npm run typecheck`
- `xcodebuild -workspace iosApp/iosApp.xcworkspace -scheme iosApp -configuration Debug -sdk iphoneos -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build`
- Live proxy `/health` and a real scan were checked after deployment. The scan returned a valid
  `menu.languageTag` using `gemini-3.5-flash-lite`.
- Encryption tests cover a successful round-trip and rejection of an incorrect passphrase.
- Repository secret audit found no real committed key material. Local secrets, Firebase config,
  `.jks`, `.keystore`, `.p8` and service-account files are ignored.

## Accessibility checklist

- [x] Existing semantic labels, headings, pane titles, live scan/error messages, 48 dp controls and
  shared reduce-motion hook were reviewed in code.
- [ ] VoiceOver on a real iPhone: deferred at the request to skip VoiceOver testing.
- [ ] TalkBack on a real Android phone: deferred.
- [ ] Maximum Dynamic Type / Android 200% font scale on real hardware: deferred.
- [ ] Physical-device interaction test of the iOS backup picker/share sheet and downloaded
  translation model: still required before production.

## iOS simulator note

ML Kit's iOS translation binary excludes Apple-silicon simulator architecture. This does not affect
real iPhones. The shared iOS test target passes, and the full app compiles for a generic physical
iPhone destination through `iosApp.xcworkspace`.
