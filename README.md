# Arcane: The Armor Collector — Android proof of concept

A **small, unofficial personal-use Android wrapper** for a classic Flash (SWF) game, built with Ruffle and an Android WebView. This project is NOT affiliated with the game's original creators or Ruffle.

**Status:** Source scaffold / untested Android prototype. The uploaded SWF reportedly runs on Ruffle Windows; Android WebView, gamepad support, mouse aiming, audio, saved games, and performance are **not yet verified**.

## Goal

- Offline 4:3 Flash rendering inside a landscape, immersive Android app
- Controller mapping tailored for AYN Odin 2 Portal (initial, experimental button mappings only)
- Later: right-stick mouse aiming, trigger attack, saved-game validation, custom icon, ES-DE entry

## Build on Windows with Android Studio

1. Copy your own `arcane-the-armor-collector.swf` into `app/src/main/assets/www/arcane.swf` (the user-only handover ZIP may already contain it).
2. Open PowerShell inside this folder and run `powershell -ExecutionPolicy Bypass -File .\prepare.ps1`. It downloads **official Ruffle 0.7.1**, prepares Android SDK configuration, and creates Gradle's official wrapper (approximately 130 MB initial Gradle download).
3. Open this project folder with Android Studio, wait for Gradle sync, then **Build > Build APK(s)**.
4. Install `app/build/outputs/apk/debug/app-debug.apk` with ADB. Test boot, sound, menus, game, and saves.

**Internet is required to prepare/build on Windows, but not to play once packaged.** The Android manifest has no `INTERNET` permission and the built app uses only packaged assets.

## Expected controls (needs testing)

Original keyboard/mouse: WASD movement; mouse aim and left-click attack; Space switches weapon; E throws; B swaps armour; R/T cycle throwable weapons. Sources: classic Flash game listing at flashga.me and FlashGames.BambouSoft.

The Android-side experimental gamepad bridge maps left stick / D-pad -> WASD, right stick -> pointer aiming, R1/R2 -> touch attack, A -> Space, X -> E, B -> B, Y -> R, and L1 -> T. It generates keyboard and pointer events for the WebView rather than relying on its gamepad API. **This mapping is not yet tested on an actual Odin**; touch interaction remains supported. Right-stick hover, attacks, and simultaneous controller/touch input require on-device validation.

## Publishing to GitHub

The recommended repository is `Arcane-Armor-Collector-Android`. `arcane.swf` is deliberately ignored by Git, as are the downloaded Ruffle assets. **Do not publish the original SWF, game art, or a redistributable APK with the game included without permission from the original rightsholders.** Keep public GitHub limited to wrapper source and setup instructions.

Ruffle: https://ruffle.rs (MIT/Apache-2.0; preserve upstream notices if you redistribute its runtime).
Android WebViewAssetLoader: https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader

## Background audio and app lifecycle

The Android wrapper now explicitly suspends Ruffle (including its sounds) when the app is backgrounded and resumes on return. It also pauses/resumes WebView timers and destroys the WebView on exit. This still needs verification on the Odin: start a fight, press Home, confirm the music stops, return, then close the app from Recent Apps.

The custom Android icon is supplied as a separate local asset pack; it is not included in this source-only repository.

## Known limitations

- Ruffle desktop compatibility doesn't guarantee WebView compatibility.
- Input mapping is provisional; Android gamepad testing is essential.
- Flash network calls are disabled; old MochiAds/highscores may not work.
- Save files use WebView local storage and may require additional changes.
- Controller-pointer bridge added, but not yet tested on-device. No finished launcher artwork or custom touchscreen overlay yet.


## Display and controller options (experimental)

A native pause/options overlay is available from the Odin's **Start** button or the small floating menu icon near the top-right of the screen. Opening the overlay calls Ruffle's suspend operation, pausing gameplay and audio; closing it resumes the game. Controller navigation uses D-pad/left stick to move, A to select, B to go back, and Start to resume. The same options can be tapped on screen.

Display settings persist through Android SharedPreferences:

- **Original 4:3**: entire Flash image with side bars.
- **Stretch to Screen**: entire Flash image stretched to widescreen.

The Flash game's native artwork remains 4:3. The former **Fill Screen** option was removed from Arcane because it crops gameplay and is unplayable on the Odin 2 Portal. Existing saved Fill settings automatically migrate to Stretch; other saved settings are unchanged. Changing mode does not reload the SWF. The controller crosshair and aiming coordinate translation use the current aspect mode.

In **Controller Settings**, the user can rebind Attack, Alternate Attack, Switch Weapon, Swap Armour, Throw, Previous Throwable, and Next Throwable. Select an action and press a physical button. If that button is already assigned, its previous action becomes **Unmapped** and shows a warning triangle; mappings never swap automatically. Unmapped actions remain inactive until assigned again or reset. When leaving Controller Settings with any unmapped actions, FrostFrame shows a warning listing them and offers **Keep Editing** or **Continue Anyway**; this warning also applies if Start is used to exit the controls menu. The Controls list uses compact rows to show more at once. Available buttons: A/B/X/Y, L1/R1/L2/R2 and L3/R3. Start is always reserved for the options menu. Invert Right-Stick Y, Aim Sensitivity and Restore Default Controls are also available. Bindings and preferences persist across launches.

Left stick/D-pad movement and right-stick aiming remain fixed in this initial controls editor, preserving the tested movement recovery and R2/cursor behaviour. All the new options require on-device verification before treating them as stable.
