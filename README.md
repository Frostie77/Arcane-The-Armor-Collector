# Arcane: The Armor Collector — Android port wrapper

A **small, unofficial personal-use Android wrapper** for a classic Flash (SWF) game, built with Ruffle and an Android WebView. This project is NOT affiliated with the game's original creators or Ruffle.

**Status:** v0.2.0-rc2 candidate. Native Android WebView/Ruffle gameplay, the Odin 2 Portal controller, FrostFrontend pause/options, aspect modes, remapping, unmapped warnings and persistent mappings have been tested on-device. **Touch controls are newly implemented and must pass on-device testing before a final release.**

## Goal

- Offline 4:3 Flash rendering inside a landscape, immersive Android app
- Tested AYN Odin 2 Portal controller mapping and remapping
- FrostFrontend pause/options, Original 4:3 and Stretch display modes
- Virtual touchscreen movement/aim joysticks and action buttons for Android touch devices
- Remaining validation: touch multi-input, first-run import flow, saving, extended stability and release packaging

## Build on Windows with Android Studio

1. Copy your own `arcane-the-armor-collector.swf` into `app/src/main/assets/www/arcane.swf` (the user-only handover ZIP may already contain it).
2. Open PowerShell inside this folder and run `powershell -ExecutionPolicy Bypass -File .\prepare.ps1`. It downloads **official Ruffle 0.7.1**, prepares Android SDK configuration, and creates Gradle's official wrapper (approximately 130 MB initial Gradle download).
3. Open this project folder with Android Studio, wait for Gradle sync, then **Build > Build APK(s)**.
4. Install `app/build/outputs/apk/debug/app-debug.apk` with ADB. Test boot, sound, menus, game, and saves.

**Internet is required to prepare/build on Windows, but not to play once packaged.** The Android manifest has no `INTERNET` permission and the built app uses only packaged assets.

## Game controls

Original keyboard/mouse: WASD movement; mouse aim and left-click attack; Space switches weapon; E throws; B swaps armour; R/T cycle throwable weapons. Sources: classic Flash game listing at flashga.me and FlashGames.BambouSoft.

The tested Odin bridge maps left stick / D-pad to WASD, right stick to aim, R1/R2 to attack, A to switch weapon, X to throw, B to armour, Y/L1 to throwable cycling. These buttons can be remapped in FrostFrontend, with unmapped controls shown explicitly. Start opens FrostFrontend.

Touch devices use virtual MOVE and AIM sticks plus FIRE, ALT, WEAPON, ARMOUR, THROW, PREV and NEXT buttons. The virtual buttons activate game actions regardless of physical controller remapping. Touch and controller inputs remain independent. This virtual layout has not yet been verified on-device.

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
- Touchscreen controls are present in v0.2.0-rc2 but still require live testing.
- Launcher icon artwork is installed locally for the developer's build; the source repository does not contain original game artwork.


## Display and controller options (experimental)

**FrostFrontend** is a larger, centered pause/options overlay accessed using the Odin's **Start** button or the floating three-line icon. Opening it pauses gameplay and audio. It has persistent **Graphics**, **Controls** and **Touch Settings** tabs, plus a boxed **X** in the top-right corner instead of a Resume Game row. Controls contains **Button Mapping** and **Movement Settings**. Use L1/R1 to change tabs, D-pad/left stick to navigate, A to select, B to go back, and Start or X to close. Touch selection also works. Exiting Button Mapping with unmapped actions still opens the warning before leaving.

Display settings persist through Android SharedPreferences:

- **Original 4:3**: entire Flash image with side bars.
- **Stretch to Screen**: entire Flash image stretched to widescreen.

The Flash game's native artwork remains 4:3. The former **Fill Screen** option was removed from Arcane because it crops gameplay and is unplayable on the Odin 2 Portal. Existing saved Fill settings automatically migrate to Stretch; other saved settings are unchanged. Changing mode does not reload the SWF. The controller crosshair and aiming coordinate translation use the current aspect mode.

In **Controller Settings**, the user can rebind Attack, Alternate Attack, Switch Weapon, Swap Armour, Throw, Previous Throwable, and Next Throwable. Select an action and press a physical button. If that button is already assigned, its previous action becomes **Unmapped** and shows a warning triangle; mappings never swap automatically. Unmapped actions remain inactive until assigned again or reset. When leaving Controller Settings with any unmapped actions, FrostFrame shows a warning listing them and offers **Keep Editing** or **Continue Anyway**; this warning also applies if Start is used to exit the controls menu. The Controls list uses compact rows to show more at once. Available buttons: A/B/X/Y, L1/R1/L2/R2 and L3/R3. Start is always reserved for the options menu. Invert Right-Stick Y, Aim Sensitivity and Restore Default Controls are also available. Bindings and preferences persist across launches.

Left stick/D-pad movement and right-stick aiming remain fixed in the physical controller editor, preserving tested movement recovery and R2/cursor behaviour.

### Touchscreen settings (v0.2.0-rc2)

Under **FrostFrontend → Touchscreen Settings**:

- **Touch Controls**: Auto (show only when no controller is detected), On or Off.
- **Control Size**: Small / Medium / Large.
- **Opacity**: Low / Medium / High.
- **Movement Pad**: Joystick or digital D-pad; the D-pad emulates discrete directions rather than the analogue deadzone.
- **Touch Aiming**: Relative or Direct. Relative (default) provides a lower right-hand touch area: swipe to move the existing crosshair, tap or hold to attack. Direct lets you tap targets in the game area using Ruffle's normal input.
- **Sides**: Standard or Swapped (exchange the movement pad and the relative-aim area/action sides).
- **Position**: Edge / Normal / Inset (distance from the screen edge).
- **Reset Touch Controls**: restore default virtual layout.

The movement view, aim touch surface, and action views handle separate finger presses. In Relative aim mode, touches outside the lower right-hand aim area and buttons pass to the original Flash game. In Direct mode the lower right-hand aim surface is absent. The on-screen buttons are independent of physical controller assignments, so an unmapped hardware action is still available from touch when touch controls are shown.

For the **AYN Odin 2 Portal**, choose Touch Controls: **On** to test the overlay despite its built-in gamepad. The current setting is saved between launches. During FrostFrontend pause/options or while Android is backgrounded, touch inputs are released and the virtual controls are hidden.

### Release checklist

- [x] Gamepad movement and native cursor aiming tested on Odin 2 Portal.
- [x] FrostFrontend options, pause, visual styling, display modes, button remapping and unmapped warning tested.
- [x] Unmapped controller settings remain saved after restarting.
- [ ] Test touch movement release and no wandering, two-finger movement + relative aiming/attack, digital D-pad directions, all five virtual secondary-action buttons.
- [ ] Test touch-control layout modes and visibility on phone/handheld.
- [ ] Test audio pause/background/return and long-session stability.
- [ ] Confirm final version and publish a source-only GitHub Release. The SWF and other unlicensed game assets must not be published with a public APK.


### Current release-candidate test priorities

The tabbed FrostFrontend layout and expanded window were tested. Its header now reads **SETTINGS**. The touch-control aim stick and separate Fire/Alt buttons were removed; Relative aim now provides a thumb-sized workflow within the lower right-hand area, moving the crosshair via drag while touching to attack. Direct tap-to-aim is retained as an option. The five secondary-action buttons remain. Users can now swap the touch movement joystick for a digital D-pad. Adjustable 4-way/8-way analogue movement, stick deadzone and axis inversion are available under **Controls → Movement Settings**. The new relative aim and virtual D-pad still require on-device testing.

**Known issue being investigated:** Arcane did not retain some completed-level progress in user testing. Do not assume saving works until we determine whether the Flash game writes save data and whether Ruffle/WebView restores it across launches. Avoid wiping app data during testing.
