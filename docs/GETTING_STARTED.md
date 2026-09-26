# Getting started (for people new to Android and Kotlin Multiplatform)

This guide takes you from nothing installed to running MenuLango on a phone, changing something,
testing it and sending it back. No Android or iOS experience needed. If a step fails, look at
[When something goes wrong](#when-something-goes-wrong) before asking for help.

---

## 1. What this project is

MenuLango is **one app written once, in Kotlin**, that runs on both Android and iPhone. That is what
"Kotlin Multiplatform" (KMP) means. The screens are built with **Compose Multiplatform**: the UI is
written as Kotlin functions marked `@Composable` instead of XML layouts or SwiftUI.

The repo has four parts:

| Folder | What it is | How often you'll touch it |
|---|---|---|
| `composeApp/` | **The app.** All screens, logic, networking and storage, shared by Android and iOS | Almost always |
| `androidApp/` | A thin Android wrapper that starts the shared app | Rarely |
| `iosApp/` | A thin iPhone wrapper (Swift) that starts the shared app | Rarely |
| `proxy/` | A small server (TypeScript, on Cloudflare) that sends menu photos to Google's Gemini AI | Only for AI or prompt changes |

Inside `composeApp/src/` the code is split by platform:

- `commonMain/`: code for **both** platforms. 95% of the work happens here.
- `androidMain/`: Android-only code (the camera, mainly).
- `iosMain/`: iPhone-only code (the camera, mainly).
- `commonTest/`: tests. They run on both platforms.

The file tree in the [README](../README.md#layout) says what each folder under `commonMain/` does.

---

## 2. Install the tools (once, about an hour, mostly downloads)

### Everyone

1. **Android Studio**, the latest stable version: https://developer.android.com/studio
   It includes the Android SDK, the emulator and a Java runtime. On first launch pick
   **Standard** setup and let it download everything.
2. In Android Studio: **Settings → Plugins → Marketplace**, search for **Kotlin Multiplatform** and
   install it. It lets you run the iOS app from Android Studio and shows platform folders properly.
3. In Android Studio: **Settings → Languages & Frameworks → Android SDK**. On the **SDK Platforms**
   tab, tick **Android API 37** and click Apply.
4. **Git**. Macs already have it (run `git --version` in Terminal; if it asks to install developer
   tools, say yes). On Windows install https://git-scm.com.
5. **Access to the repo.** Ask Alajemba to add your GitHub account as a collaborator on
   https://github.com/alajemba-vik/Menulango, then accept the email invite.

### Mac users who want to run the iPhone version

6. **Xcode** from the Mac App Store. It's large, so start the download early. Open it once so it can
   install its components, including the **iOS platform/simulator**.
7. In Terminal: `sudo xcode-select -s /Applications/Xcode.app` (it asks for your Mac password).
8. Install CocoaPods: `sudo gem install cocoapods` (or `brew install cocoapods` if you use Homebrew).

Windows and Linux can't build the iPhone app. That's fine: work on Android, and because the code is
shared the iPhone app gets your change too. CI checks the iOS side for you (see section 6).

### Only if you'll work on the proxy

8. **Node.js 24**: https://nodejs.org (the LTS installer).

---

## 3. Get the code and open it

```bash
git clone https://github.com/alajemba-vik/Menulango.git
cd Menulango
```

In Android Studio: **File → Open**, select the `Menulango` folder (the one containing
`settings.gradle.kts`), and click **Trust Project**.

The first open runs a **Gradle sync**, which downloads every library the project uses. It takes
5–15 minutes the first time; the progress bar is at the bottom right. Wait for it to finish before
clicking anything else. Later syncs take seconds.

> **Gradle** is the build tool. `./gradlew something` in a terminal is the same as clicking a button in
> Android Studio. `gradlew` is a script in the repo that downloads the right Gradle version for you,
> so you never install Gradle yourself.

---

## 4. Run the app

You don't need any keys or accounts to run it. With no configuration the app runs in debug mode on a
built-in **sample menu** (a Greek taverna).

### On an Android emulator (a virtual phone on your computer)

1. **Tools → Device Manager → + (Create Virtual Device)**. Pick a **Pixel** phone, choose the newest
   system image (download it if asked) and click Finish.
2. At the top of Android Studio choose **androidApp** in the run-configuration dropdown and your
   emulator in the device dropdown.
3. Click the green **▶ Run** button. The first build takes a few minutes; later ones are much faster.

### On your own Android phone

1. On the phone: **Settings → About phone → tap "Build number" 7 times** to enable developer options.
   Then **Settings → System → Developer options → USB debugging: on**.
2. Plug it in by USB and accept the "Allow USB debugging?" prompt on the phone.
3. Pick the phone in the device dropdown and click **▶ Run**.

Or from a terminal: `./gradlew :androidApp:installDebug`

### On an iPhone (Mac only)

- First run `cd iosApp && pod install`, then open the generated workspace with
  `open iosApp.xcworkspace`. Do not open `iosApp.xcodeproj` directly.
- In Xcode, pick your connected iPhone and press **⌘R**. The first build is slow because it compiles
  all the shared Kotlin code.

A real iPhone also needs an Apple developer account and a `TEAM_ID`. Ask Alajemba. Waiter-note
translation uses ML Kit models on the phone; that library deliberately excludes Apple-silicon
simulators, so translation must be tried on an iPhone.

### Things to try once it's running

- The capture screen has a **Sample menu** button: it loads the Greek taverna menu as if a real photo
  had been scanned. Use this for almost all your testing.
- The **Debug: Plus** toggle unlocks the paid screens ("Help me choose") without buying anything.
- Both appear **only in debug builds**. Store users never see them.
- Scanning a **real photo** needs the proxy (section 7). Without it you'll get an error screen, and
  that error screen is worth checking too.

---

## 5. Make a change

### Your first change (to learn the loop)

1. Open `composeApp/src/commonMain/composeResources/values/strings.xml`. All the text shown in the
   app lives here.
2. Change a piece of text you can see on the capture screen.
3. Click **▶ Run** again and watch your text appear on Android and, if you're on a Mac, on iOS too.
   One change, two apps.

### Previews: see a screen without running the app

Open `composeApp/src/commonMain/kotlin/com/menulango/preview/ScreenPreviews.kt` and click **Split**
(top right of the editor). Android Studio draws the screens beside the code and redraws them when
you edit. It's much faster than running the app for layout or colour changes.

### Rules the code follows (so your change fits in)

- **Colours, text styles, spacing and animation timings** come from `core/design/`. Don't type a
  colour or a `16.dp` straight into a screen; use the tokens.
- **Every screen** has a ViewModel that exposes one `StateFlow<UiState>`, and the state is always one
  of `Loading`, `Ready`, `Empty` or `Failed`. Screens (composables) never call the network or the
  database directly; they only read state and send events to the ViewModel.
- **Network and cache** meet only in `data/menu/MenuRepository.kt`.
- **Payments** (RevenueCat) are only touched in `data/billing/BillingRepository.kt`.
- **User-visible text** goes in `strings.xml`, never hard-coded in Kotlin.
- If something must work differently on Android and iOS, it goes in `androidMain/` and `iosMain/`
  with an `expect`/`actual` pair. `platform/ImageCapture.kt` is the example. Try hard to avoid
  this; almost everything can be shared.

When unsure, find the nearest similar screen or file and copy how it does things.

---

## 6. Test your change

### Automatic tests

The tests are in `composeApp/src/commonTest/`. They're plain Kotlin that checks the logic: the free
scan counter, how dishes are picked, how the AI's reply is read. Run them before every push:

```bash
./gradlew spotlessApply                        # fixes code formatting for you
./gradlew spotlessCheck                        # confirms formatting is clean (CI fails otherwise)
./gradlew :composeApp:testAndroidHostTest      # runs the tests (fast, any computer)
./gradlew :composeApp:iosSimulatorArm64Test    # same tests on iOS (Mac only, slower)
```

In Android Studio you can also open any test file and click the green ▶ next to a test.

**Writing a test:** copy the style of an existing one.
`composeApp/src/commonTest/kotlin/com/menulango/data/quota/ScanQuotaTest.kt` is short and a good
model. A test is a function marked `@Test` that sets something up, does one thing, and checks the
result with `assertEquals` or `assertTrue`. If you fix a bug in logic, add a test that would have
caught it.

### Test by hand

Before you call a change done, click through it on a device:

- [ ] Sample menu → the menu list → open a dish → close it with the back gesture
- [ ] With **Debug: Plus** on: "Help me choose" and each choosing mode
- [ ] With **Debug: Plus** off: the paywall appears where it should
- [ ] **Dark mode** (Settings → Display → Dark theme on the phone): everything still readable
- [ ] **Largest text size** (Settings → Display → Font size): nothing cut off or overlapping
- [ ] Rotate the phone or use a small screen: nothing broken
- [ ] If you changed shared UI and have a Mac: check the iPhone simulator too

### CI: the robot that checks every push

Each push and pull request triggers GitHub Actions (`.github/workflows/ci.yml`), which runs the
formatting check, the tests on Android **and** iOS, a debug build, and the proxy tests. Watch it on
the repo's **Actions** tab or at the bottom of your pull request. A red ✗ means something failed:
click it and read the log. The error is usually in the last 30 lines.

---

## 7. Real menu photos (optional)

To scan real photos, the app needs the address of the proxy. Create a file called
`secrets.properties` in the repo root (it's git-ignored and **must never be committed**):

```properties
PROXY_URL=https://menulango-proxy.<ask-alajemba>.workers.dev
```

Rebuild, and the camera will do real scans. Each scan costs real money (a fraction of a cent), so use
the **Sample menu** for everyday testing.

**Fake proxy, no internet or AI needed:** with Node installed, run `node proxy/scripts/mock-proxy.mjs`.
It serves the sample menu like the real server and can simulate failures:

```bash
node proxy/scripts/mock-proxy.mjs                         # normal
MOCK_ERROR=RATE_LIMITED node proxy/scripts/mock-proxy.mjs # server says "too many requests"
MOCK_EMPTY=1 node proxy/scripts/mock-proxy.mjs            # no dishes found
MOCK_CUT=1 node proxy/scripts/mock-proxy.mjs              # connection drops halfway
```

Then on the Android emulator run `adb reverse tcp:8787 tcp:8787` and set
`PROXY_URL=http://localhost:8787` in `secrets.properties`. Use this to check the error and empty
screens.

For iOS, copy `iosApp/Configuration/Secrets.xcconfig.example` to `Secrets.xcconfig` and follow the
comments inside it.

---

## 8. Share your work with Git

Never work directly on `main`. For each piece of work:

```bash
git checkout main
git pull                                  # get everyone's latest work
git checkout -b fix-dish-sheet-padding    # a new branch with a short, descriptive name
# ...make your change, run the tests...
git add -A
git commit -m "Fix padding at the bottom of the dish sheet"
git push -u origin fix-dish-sheet-padding
```

Then open the repo on GitHub. It offers a **Compare & pull request** button. Describe what you
changed and how you tested it (a screenshot helps a lot), wait for CI to go green, and ask someone to
review. Once approved, click **Merge**.

Android Studio can do all of this with buttons too (the **Git** menu and the branch name at the top
left), if you prefer that to the terminal.

**Never commit:** `secrets.properties`, `Secrets.xcconfig`, `*.jks` keystore files, or any API key.
`.gitignore` already blocks these; if Git ever shows one as a new file, stop and ask.

---

## When something goes wrong

| Problem | Fix |
|---|---|
| Gradle sync fails or code is all red | **File → Sync Project with Gradle Files**. If that doesn't help: **File → Invalidate Caches → Invalidate and Restart** |
| "SDK location not found" / "Android SDK 37 missing" | Install API 37 (section 2, step 3). Android Studio creates `local.properties` pointing to the SDK |
| "Unsupported Java version" | **Settings → Build Tools → Gradle → Gradle JDK**: pick the one bundled with Android Studio (17 or newer) |
| `spotlessCheck` fails in CI | Run `./gradlew spotlessApply`, commit and push again |
| Emulator is very slow or won't start | Close other heavy apps. On Windows, enable virtualization in the BIOS. Or use a real phone |
| Phone not listed in the device dropdown | Re-plug it, accept the USB debugging prompt, try another cable (some only charge) |
| iOS build fails with "xcode-select" / "no iOS platform" | Section 2, steps 6–7; in Xcode: **Settings → Components** and install the iOS platform |
| Real scan shows an error | Normal without a proxy. Use **Sample menu**, or set `PROXY_URL` (section 7) |
| Everything is weird and nothing helps | `./gradlew clean`, then Run again |

Still stuck? Copy the **first** red error line (not the last) and send it along with what you
were doing.

## Learning more

- Kotlin basics, interactive: https://kotlinlang.org/docs/kotlin-tour-welcome.html
- Compose basics (the ideas are identical in Compose Multiplatform):
  https://developer.android.com/develop/ui/compose/tutorial
- Kotlin Multiplatform overview: https://kotlinlang.org/docs/multiplatform.html
- How MenuLango itself works: the [README](../README.md#how-it-works)
