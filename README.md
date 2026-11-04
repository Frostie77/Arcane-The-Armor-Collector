# Arcane: The Armor Collector — Android port wrapper

A **small, unofficial personal-use Android wrapper** for a classic Flash (SWF) game, built with Ruffle and an Android WebView. This project is NOT affiliated with the game's original creators or Ruffle.

**Status:** v0.2.0-rc4 candidate. Native Android WebView/Ruffle gameplay, the Odin 2 Portal controller, FrostFrontend pause/options, aspect modes, remapping, unmapped warnings and persistent mappings have been tested on-device. **Touch controls are newly implemented and must pass on-device testing before a final release.**

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
- Touchscreen controls are present in v0.2.0-rc4 but still require live testing.
- Launcher icon artwork is installed locally for the developer's build; the source repository does not contain original game artwork.


## Display and controller options (experimental)

**FrostFrontend** is a larger, centered pause/options overlay accessed using the Odin's **Start** button or the floating three-line icon. Opening it pauses gameplay and audio. It has persistent **Graphics**, **Controls** and **Touch Settings** tabs, plus a boxed **X** in the top-right corner instead of a Resume Game row. Controls contains **Button Mapping** and **Movement Settings**. Use L1/R1 to change tabs, D-pad/left stick to navigate, A to select, B to go back, and Start or X to close. Touch selection also works. Exiting Button Mapping with unmapped actions still opens the warning before leaving.

Display settings persist through Android SharedPreferences:

- **Original 4:3**: entire Flash image with side bars.
- **Stretch to Screen**: entire Flash image stretched to widescreen.

The Flash game's native artwork remains 4:3. The former **Fill Screen** option was removed from Arcane because it crops gameplay and is unplayable on the Odin 2 Portal. Existing saved Fill settings automatically migrate to Stretch; other saved settings are unchanged. Changing mode does not reload the SWF. The controller crosshair and aiming coordinate translation use the current aspect mode.

In **Controller Settings**, the user can rebind Attack, Alternate Attack, Switch Weapon, Swap Armour, Throw, Previous Throwable, and Next Throwable. Select an action and press a physical button. If that button is already assigned, its previous action becomes **Unmapped** and shows a warning triangle; mappings never swap automatically. Unmapped actions remain inactive until assigned again or reset. When leaving Controller Settings with any unmapped actions, FrostFrame shows a warning listing them and offers **Keep Editing** or **Continue Anyway**; this warning also applies if Start is used to exit the controls menu. The Controls list uses compact rows to show more at once. Available buttons: A/B/X/Y, L1/R1/L2/R2 and L3/R3. Start is always reserved for the options menu. Invert Right-Stick Y, Aim Sensitivity and Restore Default Controls are also available. Bindings and preferences persist across launches.

Left stick/D-pad movement and right-stick aiming remain fixed in the physical controller editor, preserving tested movement recovery and R2/cursor behaviour.

### Touchscreen settings (v0.2.0-rc4)

Under **FrostFrontend → Touchscreen Settings**:

- **Touch Controls**: Auto (show only when no controller is detected), On or Off.
- **Control Size**: Small / Medium / Large.
- **Opacity**: Low / Medium / High.
- **Movement Pad**: Joystick or digital D-pad; the D-pad emulates discrete directions rather than the analogue deadzone.
- **Touch Aiming**: Relative or Direct. **Relative (default)** now automatically uses direct mouse-style touch in Arcane's inventory, level selection, and other menu scenes; it switches to relative crosshair aiming as the arena scene begins, then back to direct when the game returns to menus. **Direct** always uses absolute screen touch coordinates. Both use the same independent multi-touch input layer, and the native cursor hides whenever Direct input is active. If an unrecognised SWF does not report its scene, relative mode uses a short-tap-direct / swipe-relative fallback.
- **Sides**: Standard or Swapped (exchange the movement pad and the relative-aim area/action sides).
- **Position**: Edge / Normal / Inset (distance from the screen edge).
- **Reset Touch Controls**: restore default virtual layout.

The movement view, invisible full-screen aim layer, and action buttons support distinct fingers, with movement and aim/attack routed independently in both aiming modes. The virtual D-pad draws four individual buttons with gaps between them; sliding between directions continues to work. The on-screen buttons remain independent of physical controller assignments.

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


**v0.2.0-rc4 pending device checks:** Direct-mode move + aim + attack at once; simultaneous movement with virtual D-pad and joystick; Relative mode aiming across the full screen without visible boxed area or fallback to Direct aiming; Direct mode hides native crosshair. Saving completed-level progress is still unresolved; do not publish a final release until tested.


### v0.2.0-rc4 scene-aware touch and save testing

- The Android asset loader temporarily instruments the user's **local** original `arcane.swf` **in memory only**; it inserts an AS2 FSCommand on the SWF's 25 main timeline frames. Original file is not modified, and the SWF remains excluded from Git.
- Arcane root timeline frame 20 is treated as arena combat; remaining root frames are menu/preparation/results. Ruffle forwards these scene markers to the Android touchscreen controller. Scene classification should be checked on a real Odin before release.
- When Relative is selected, a menu phase enables **Direct** touch for inventory selection. When the arena begins, the touch layer uses **Relative** swipe/hold-to-attack automatically. On returning to menus, it restores Direct. Selecting Direct explicitly keeps Direct regardless of phase.
- Ruffle `allowNetworking: 'none'` was preventing the Flash SharedObject local-save API and FSCommand. We use `allowNetworking: 'all'` **inside Ruffle** so saving and scene notifications can function; Android still declares **no INTERNET permission**, and the WebView only serves trusted appassets URLs and rejects external requests. The application is not granted internet access.
- **Validation needed:** inventory touch selects equipment; entering/exiting arena switches modes automatically; tapping/holding attacks with movement working; completed level progress is retained after force-stop and relaunch. Do not clear Android app data during this test.


### Diagnostic build v0.2.0-rc5-debug (not a public release)

The previous scene callback only reported `menu` even when the user entered combat. Instrumentation now sends each SWF root-frame index before the frame's own ActionScript; logcat includes `ArcaneRootFrame` and `ArcanePhase` entries to verify the actual menu → arena transition. Ruffle also logs `ArcaneSaveDiag` (WebView localStorage key names and value lengths only, never save contents) at startup, every 15 seconds, and after writes. This separates cases where the game never saves, Ruffle never writes, and the WebView fails to persist data. The Android app data and original SWF are not reset. The spaced D-pad now uses font-independent vector arrows centred geometrically in each button. Do not publish this diagnostic APK as a finished release.


### v0.2.0-rc6-debug: targeted scene and save fixes

The full original Arcane SWF was inspected. The old frame-20-as-arena assumption was wrong: frame 20 contains inter-level advertising/results code. The gameplay setup scripts occur on frames 3-5 and 11-18; these are now reported as arena in the in-memory root timeline instrumentation. The full frame diagnostics remain in logcat, so gameplay transitions can be verified from the Odin instead of inferred from a static guess.

The original SWF gets `savefile = SharedObject.getLocal("Arcane8")` but its game code does not explicitly call `savefile.flush()`; its only `flush` string is from an unrelated MochiServices library. Ruffle's web backend uses browser localStorage for SharedObject data, and the previous user's log showed `localStorage=0` before and after restarting. This diagnostic build **adds in-memory AVM1 actions** to schedule `_root.savefile.flush()` every five seconds and on root scene frame transitions. It does not rewrite the save object or modify the original SWF on disk. **Unverified:** whether the Android WebView actually receives the expected storage writes and whether completed level progress persists after relaunch. Do not clear app data during tests.

The virtual D-pad arrows are drawn as geometrically centred vectors rather than relying on Unicode font metrics. This version is for testing and must not be publicly released as final.


### v0.2.0-rc7-debug: final-results Direct transition and combat HUD taps

An on-device v0.2.0-rc6 test confirmed Relative mode activates when the game enters root frame 13. The results screen remains on that *same* root frame until Continue is chosen, so root-frame detection alone cannot revert to Direct promptly. The original SWF's main script contains the `onend` function, which attaches the `finalresults` clip. The in-memory SWF patch now inserts one host-only FSCommand at the beginning of that existing function. This should return to Direct on the **post-fight Continue/Retry screen**; selecting Retry should return to Relative upon re-entering an arena frame. Verify both branches on the device.

While Relative combat mode is active, **quick taps** send direct mouse clicks to the game so potion icons, the game's own options menu and stats menu are accessible regardless of their positions. **Swiping or holding at least 170 ms** uses relative crosshair movement and holds attack. This avoids guesswork about invisible button hit zones and leaves the virtual movement pad and other mapped actions independent for multi-touch. Verify that firing still feels responsive; 170 ms may need tuning.

**Saving:** on-device logcat confirmed the original `Arcane8` browser-storage object exists and changed from 156 to 496 characters, and the user subsequently confirmed **save progress survives a reset**. Keep the in-memory save flushing logic in place, and do not delete app data when updating.


### v0.2.0-rc8-debug: relative aiming isolation

The user reported that touching anywhere in a Relative-mode arena caused the fighter to turn towards the finger. The rc7 quick-tap passthrough was incorrect: it called `tapTouchDirect(x, y)` for every tap, temporarily moving the aim to the direct screen position. In rc8, ordinary short taps in Relative combat use `tapTouchRelative()` and attack at the **existing native crosshair** without changing its coordinates; swiping and holding still use relative trackpad aiming. True Direct mouse input remains active in inventory, Continue/Retry, and menus based on the existing frame/fight-end scene notifications. **The existing combat HUD icons are no longer directly clickable via an arbitrary quick tap in Relative mode**; their exact hit regions must be calibrated with a device gameplay screenshot or reliably recovered from the SWF before adding invisible direct-touch hotspots. Never route the entire arena through direct taps again. The working Arcane8 save flush, D-pad drawing and scene detection were not changed.
