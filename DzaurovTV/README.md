# Dzaurov TV

A glass, iOS-style home screen (launcher) for the Homatics Box R 4K Plus and other Android TV 11+ boxes.
Kotlin + Jetpack Compose for TV. Package: `com.dzaurov.tv`.

## What it does (2.0)

- **Glass, iOS-style Home**: frosted-glass tiles with each app's real icon as an iOS squircle,
  tinted in the app's colour when focused. Focus lifts, grows, swings in from the side you came
  from and a highlight sweeps across. Rows cascade in when Home opens.
- **Opening an app** zooms its tile to full screen (iOS style). Holding OK springs a menu open;
  in Move mode all favorites wiggle like the iPhone home screen.
- **Backgrounds**: Alatau (snowy peaks and a mountain lake at dusk), Aurora (northern lights),
  Glow (luminous silk, phone-wallpaper style), Forest, Dusk, Night, Fog, your own photo, or
  **Auto**, which follows the time of day (misty morning → forest → Alatau sunset → aurora night).
- **Continue watching** under Favorites (system Watch Next list, needs one permission, see below),
  **Recent** below it; scrolling down slides the title away like Apple TV.
- **Control Center** (toggles button in the header): Wi-Fi, Bluetooth, Picture, Sound, Sleep timer,
  Sleep now, Screensaver, Theme, Performance, Dzaurov TV and Android TV settings.
- **Screensaver → sleep**: after 5 min idle (2/5/10/15/30/off) a lock-screen style clock with a
  slowly moving background; after 30 min of that (15/30/60/120/never) the box sleeps and the TV
  follows over HDMI-CEC. **Sleep timer** (15–120 min) works even while a film plays.
- **Dynamic Island** messages, a liquid nav lens, soft navigation clicks (system sounds).
- **All apps** with a NEW mark for apps installed in the last 3 days; **Search**; **Weather** for
  Almaty (Open-Meteo); **Settings** for all of the above. No ads, tracking or analytics.

## Home button and sleep helper

Android lets an app open itself on the Home button and switch the box off only through an
accessibility service. Dzaurov TV ships a tiny one that only notices *which app opens* and never
reads the screen. Switch it on once:

- On the box: Settings → Device Preferences → Accessibility → "Dzaurov TV · Home button and sleep" → On
- Or with ADB:
  ```
  adb shell settings put secure enabled_accessibility_services com.dzaurov.tv/com.dzaurov.tv.system.DzAccessibilityService
  adb shell settings put secure accessibility_enabled 1
  ```

Without it everything else works; the screensaver just ends in a dark screen and the box's own
sleep timer takes over. With it, Button Mapper is no longer needed.

## Remote

| Button | What it does |
| --- | --- |
| ◀ ▶ ▲ ▼ | Move focus |
| OK | Open |
| Hold OK | Options (Move, Remove, Add to Home, Hide, App info) |
| Back | Close menu → leave Move mode → back to Home. Never leaves an empty screen. |
| Home | Back to Home (once Dzaurov TV is the Home app) |

## Build on GitHub (no Android Studio needed)

1. Create an empty repository on GitHub, for example `dzaurov-tv`.
2. Upload the contents of this folder (keep the `.github` folder).
3. Open the **Actions** tab. The **Build APK** workflow runs on every push (about 5–8 minutes).
4. When it's green, the APK is at
   `https://github.com/<your-name>/dzaurov-tv/releases/latest/download/DzaurovTV-debug.apk`
   (also attached to the run as an artifact).

## Build in Android Studio (Windows, 8 GB RAM)

1. Install **Android Studio** (Koala or newer). In the setup wizard choose *Standard*; it installs
   the Android SDK. Skip creating an emulator, it needs a lot of RAM.
2. Close Chrome and other heavy apps while building.
3. **File → Open…** and pick this folder. Wait for "Gradle sync" to finish (first time 5–15 minutes,
   it downloads Gradle and libraries).
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. The APK is in `app\build\outputs\apk\debug\app-debug.apk`.
6. If the build runs out of memory, open `gradle.properties` and change `-Xmx2048m` to `-Xmx1536m`.

The project includes a fixed debug signing key (`app/debug.keystore`), so APKs from GitHub and from
your laptop install over each other and keep your favorites and settings.

## Install on the box

Allow unknown apps for Downloader, open the release link above in Downloader, install, then turn
on the helper (section above) so the Home button opens Dzaurov TV.

If a build fails on GitHub, open the failed run: its Summary page has a short error report to copy.

## Turn on Continue watching

On first start the app asks Android for access to the "Watch Next" list. If no dialog appears or
Settings → Continue watching still says Off, run once from a computer with ADB:

```
adb shell pm grant com.dzaurov.tv com.android.providers.tv.permission.READ_EPG_DATA
```

Undo: the same command with `revoke`. Some apps (Netflix especially) share their list only with
Google's own home screen, so they may not appear even with the permission.

## Performance rules this code follows

- Only scale, alpha and translation are animated. Tiles never change size.
- No live blur. Glass draws a pre-blurred 320×180 copy of the background behind each panel.
- Backgrounds are 1920×1080 JPEGs under 500 KB, decoded once. Banners load off the main thread
  (max 3 at a time) into a 24 MB cache.
- Max 12 particles; Performance mode turns off fog, particles, glow and the tile gloss.

## Credits

Geist font by Vercel, SIL Open Font License (see `FONT_LICENSE_Geist_OFL.txt`).
Weather data by [Open-Meteo](https://open-meteo.com), CC BY 4.0.
