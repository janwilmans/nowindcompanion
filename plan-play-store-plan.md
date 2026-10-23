# Publishing Nowind Companion on Google Play

Plan to sell the Android app on Google Play for **€10**, with **free access for beta testers**.
Status as of 23 October 2026. App ID: `nl.nullptr.nowindcompanion` (the Kotlin namespace stays `nl.myquest.nowindcompanion`).

## Decisions

| Topic | Decision |
|---|---|
| Developer account | The existing Google Play developer account |
| Price model | **Paid app, €10.** No in-app purchase, no license check |
| Beta testers | Free, via the internal test track and promo codes (see [Beta testers](#beta-testers-for-free)) |

## Play Store rules that shape this plan

- **A free app can never become paid.** Play only allows paid → free. The first public release must therefore already be paid.
- **The app ID is permanent** once an app is published. It is `nl.nullptr.nowindcompanion`.
- **Paid apps and testers:**
  - **Internal testing** (up to 100 testers): testers install the paid app **for free**.
  - **Closed and open testing**: testers **must buy** a paid app, unless they redeem a promo code.
- **Promo codes** give the paid app away for free. Up to 500 codes per quarter, redeemable in the Play Store app.
- **New personal accounts.** Personal developer accounts created after 13 November 2023 must run a **closed test with at least 12 testers, opted in for 14 continuous days**, before production can be requested. Check the Play Console dashboard to see whether this applies to our account.
- **Target API level.** Since 31 August 2026 new apps and updates must target **API 36** (Android 16). Extensions to 1 November 2026 could be requested. The app now targets API 36.
- **Money.** Prices include VAT in the EU, and Google remits the VAT. Google's service fee is 15% on the first $1M per year. €10 in the Netherlands (21% VAT) leaves about **€7.00 per sale**.

## Part 1 – Changes to the app

All in `apps/Nowindcompanion/app`. Each step is its own commit.

1. **Target and compile SDK 36.** Done in `build.gradle.kts`.
   - `d2xx.jar` crashes when targeting 34+ (mutable implicit `PendingIntent`, `registerReceiver` without an export flag). `D2xxCompat` does that setup itself; FTDIClient must get its `D2xxManager` from `D2xxCompat.getInstance`. Test the USB permission dialog on a real phone.
   - The current libraries build fine with SDK 36. Newer AndroidX releases require minSdk 23, so raise them together with minSdk, not now.
   - Check the behaviour changes for targeting 34–36:
     - **Foreground service `connectedDevice`.** From target 34 it needs a prerequisite permission or a granted USB device. `NowindApp` only runs `HostingService` while "keep hosting" is on **and** the interface is connected (`ConnectionState.Hosting`), so USB access has been granted by then. Don't add `CHANGE_NETWORK_STATE` as a workaround: the app doesn't need it, and the Play foreground service policy only allows `connectedDevice` while a device is in use. Check that `HostingService` still starts after the interface connects.
     - **Edge-to-edge** is enforced from target 35. This is already done (`enableEdgeToEdge()` in `MainActivity`). Check that no content hides behind the system bars.
     - **Predictive back** applies from target 35/36. `BackHandler` in `BrowseScreen` should keep working; test the back gesture on every screen.
2. **Release signing.**
   - Create an upload keystore and keep it outside the repo.
   - Add `signingConfigs.release`, which reads `keystore.properties` (store file, passwords, alias).
   - Add `keystore.properties`, `*.jks` and `*.keystore` to `.gitignore`.
   - Enrol in **Play App Signing**, so that Google holds the app signing key and a lost upload key can be replaced.
3. **Versioning.**
   - Set `versionName = "1.0.0"`.
   - Increase `versionCode` for every upload; Play rejects a repeated code.
4. **App icon.**
   - Replace the default Android launcher icon (`res/mipmap-*`, `ic_launcher*.xml`) with an adaptive icon based on the Nowind artwork.
   - The Play listing also needs a 512×512 icon.
5. **Release defaults.**
   - "Enable debug information" defaults to **on** today (`Settings.debugEnabled` in `SettingsRepository.kt`). Make it default to off in release builds, via `BuildConfig.DEBUG` with `buildConfig = true`.
6. **R8 / minify.** Leave it off for 1.0, as it is now. If it is turned on later, add `-keep class com.ftdi.** { *; }` for `libs/d2xx.jar`.
7. **Build the App Bundle.** Play only accepts `.aab`:
   ```
   ./gradlew bundleRelease   →   app/build/outputs/bundle/release/app-release.aab
   ```
8. **Repo housekeeping.** Mark binaries in `.gitattributes` (`*.jpg binary`, `*.png binary`, `*.zip binary`, `*.jar binary`). `* text eol=lf` currently treats them as text and would corrupt them when they are re-added.

## Part 2 – Play Console steps

Done by the account owner, in this order.

1. **Payments profile.** Setup → Payments profile. It is required before a price can be set.
   - Declare **trader status** under the EU Digital Services Act. A seller of paid apps is a trader, so the address, phone number and e-mail are then shown on the store listing.
2. **Create the app.** Name "Nowind Companion", type App, **Paid**.
3. **Store listing.**
   - Short description (80 characters) and full description.
   - 512×512 icon and a 1024×500 feature graphic.
   - At least 2 phone screenshots: menu, Browse, Nowind status, Settings.
4. **App content forms.**
   - **Privacy policy URL.** A short page, e.g. on nullptr.nl. It should state:
     - No personal data is collected.
     - Disk images are downloaded from the sources the user configures.
     - Settings stay on the device.
   - **Data safety.** No data collected or shared. Downloads use HTTPS where the source supports it. Plain HTTP sources are allowed (`usesCleartextTraffic`), so do not claim "all data encrypted in transit".
   - **Foreground service declaration.** Required for apps targeting Android 14+ that use foreground service permissions.
     - Declare `connectedDevice`.
     - Explain that it keeps disk hosting running for the MSX over the USB interface while the screen is off.
     - Google asks for a short video showing the feature.
   - **Content rating** questionnaire, **target audience** (13+), **ads** (none), **app access** (no login needed).
   - **Review notes.** Reviewers have no Nowind interface. Explain:
     - The USB hardware is needed for hosting.
     - What can be tested without it: Browse, downloading, the disk library, Settings.
5. **Pricing and countries.** Set **€10.00** as the default price; Google converts it to other currencies and individual countries can be adjusted. Choose the countries.
6. **Internal testing.** Free for testers, up to 100.
   - Create an e-mail list of testers.
   - Upload the `.aab` and roll out.
   - Send testers the opt-in link. They install from Play without paying.
7. **Closed testing.** Only needed if the account falls under the 12-tester rule.
   - Create a closed track with **at least 12 testers**. Plan for 15–20: the 14-day count restarts if fewer than 12 stay opted in.
   - Create **promo codes** (Monetize → Promotions → paid app) and send one to every tester so they don't pay.
   - After 14 days, apply for production access on the dashboard. Google asks about the test.
8. **Production.** Promote the tested release. A first review usually takes a few days.

## Beta testers for free

| Situation | Route |
|---|---|
| Before and after launch, up to 100 people | **Internal testing track.** Free install of the paid app, updates through Play |
| Closed test for the 12-tester rule | **Promo codes**, one per tester |
| Anyone else (reviewers, MSX club members) | **Promo codes**, 500 per quarter |

## Risks to resolve before submitting

- **Copyrighted content policy.** Play removes apps that facilitate downloading copyrighted material without permission.
  - **The risk:** the default source is the File-Hunter Nowind API (`api.file-hunter.com/nowind.php`), which serves MSX game disks.
  - **Decision:** the app ships with **no** built-in source name. The default source is empty and off until the user enters a name in Settings. A name without dots stands for `https://api.<name>.com/nowind.php`, and the app never shows the expanded URL. The name `file-hunter` is not stored anywhere in the app.
  - The "Add source" dialog used to suggest three game archives as examples. That counts as steering users to game downloads, so the examples have been removed. Don't add game archives back as suggestions.
  - Ask Arnaud de Klerk (file-hunter.com) for **written permission** to use the API from a paid app, and mention it in the review notes. Don't submit without it.
  - **Store listing wording:** describe the app as the host for the Nowind interface and the archive browser as an optional extra.
  - **The listing screenshots** should not prominently show commercial game titles.
  - Without that permission, the safer route is to ship with no built-in source and let users add their own.
- **FTDI D2XX library license.** Check that the license of `libs/d2xx.jar` allows redistribution in a paid app. FTDI permits it for use with FTDI chips, but confirm this for the version we ship.
- **Hardware app review.** Reviewers can only test the parts that work without the interface. Good review notes and a short demo video help.
- **Writing to disks is not implemented.** The MSX can't save to disk yet. Mention it in the description so buyers know.

## Open questions

- **Account type and age.** Does the account fall under the 12-tester closed-test rule? See the Play Console dashboard.
- **Permission from file-hunter.com** for the built-in default source.
- **Privacy policy hosting:** nullptr.nl?

## Verification before every release

- `./gradlew assembleDebug testDebugUnitTest` passes.
- `./gradlew bundleRelease` produces a signed bundle, and `jarsigner -verify -verbose app-release.aab` shows the upload key.
- On a real phone with the Nowind interface:
  - Hosting boots the MSX from a hosted disk.
  - The USB "use by default" association works.
  - "Keep hosting in the background" keeps working with the screen off.
- On an API 36 emulator: the menu, Browse (download and host), Settings (adding a source), and the back gesture on every screen.
- After the internal-testing rollout: install from Play on a tester account and confirm that it is free for the tester.

## Sources

- Testing paid apps (internal testing is free, closed and open testers must buy): https://support.google.com/googleplay/android-developer/answer/9845334
- Promo codes for paid apps: https://support.google.com/googleplay/android-developer/answer/6321495
- 12 testers for 14 days for new personal accounts: https://support.google.com/googleplay/android-developer/answer/14151465
- Target API level requirements: https://developer.android.com/google/play/requirements/target-sdk
- A free app can't be changed to paid: https://support.google.com/googleplay/android-developer/answer/6334373
