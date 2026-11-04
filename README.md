# Arcane: The Armor Collector — Android port wrapper

A **small, unofficial personal-use Android wrapper** for a classic Flash (SWF) game, built with Ruffle and an Android WebView. This project is NOT affiliated with the game's original creators or Ruffle.

**Status:** v0.2.0 game-free public APK candidate. Odin gamepad, touchscreen movement/aim/HUD selections, Arcane8 saves and current FrostFrontend header/layout have been tested in private Debug builds. The new first-run SWF import flow and separately signed public build still require device verification before publishing.

## Public APK: supply your own game file

The public APK **does not contain Arcane's original Flash game or copyrighted artwork**. It includes the Android wrapper and licensed Ruffle runtime. On first launch:

1. Tap **Select Game File** (or press A on a controller).
2. Choose your own legally obtained Arcane: The Armor Collector `.swf` file from Android's document picker.
3. Wait for the file to be copied into the app's private storage. The game then starts normally, and the copy remains available on future launches.

No internet connection or all-files storage permission is required to import or play; internet is used only for optional public update checks. Cancelling the picker leaves the import screen visible. An invalid or oversized file is rejected without replacing any previously imported game. The app keeps its WebView/Flash save data separately from the user-selected game file.

**Existing private Debug installs:** Do not uninstall or clear app data to try a public release build; that would erase progress. A normally signed release APK cannot update a Debug-signed installation with the same package ID. Use the isolated import test below first; it has a separate application ID and leaves Debug data untouched.

## Build on Windows

1. Clone the source and run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\prepare.ps1` to prepare Ruffle/Gradle. The original SWF is **not required** to build a public wrapper.
2. To build and install a safe, separate game-free import test on a connected Odin, run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\test-public-import.ps1`. If your private original game SWF exists at `app/src/main/assets/www/arcane.swf`, the script temporarily removes it from the APK build and optionally copies it to the Odin's Downloads folder for selection. Your main Debug installation and save data remain intact.
3. Once first-run import, game controls, Updates navigation and saves are verified on this isolated test, run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-public-release.ps1` to produce a **signed and verified** game-free APK in the local `release` folder. It uses a locally created Android release keystore, prompts for the signing password and reports the SHA-256 hash. **Back up your release keystore securely**; losing it can prevent installing future updates over an existing release.

Both scripts refuse APKs that contain `arcane.swf` and restore any temporarily moved personal game file. Ruffle runtime assets are downloaded by `prepare.ps1`, not committed to this source repository.

## Live Android updates (FrostFrontend)

FrostFrontend now has **Graphics | Controls | Touch Settings | Updates**, with the original compact header size preserved. **Updates** displays installed version, latest public version, check status, **Check for Updates**, **Check Automatically** (saved on/off, default on), and **View Release** when a verified release exists.

Like Tooie Recompiled, this is a **release-page updater**, not a silent or in-app APK installer. It checks in a background thread, never reads game files or WebView saves, and opens the matching official GitHub release in an external browser. Users choose whether to download and install the update; Android keeps app data on a properly signed in-place update.

Update eligibility uses Android version **codes** (not version-name comparison), package name, the APK's installed signing-certificate fingerprint, minimum SDK, a published non-prerelease GitHub tag, and an exact APK asset name, size and SHA-256. A pinned HTTPS metadata location at `updates/android-stable.json` initially contains `latest: null`; it must only be changed to point at the signed, published APK **after** release. The release build script stages a matching `release/android-stable.json` from the actual signed APK and its signing certificate. Do not publish unverified or unpublished candidate metadata.

Automatic checks run when the signed release launches, if enabled. Verified results are cached for six hours; failed automatic checks back off for fifteen minutes, while manual checks always refresh. Since Arcane launches directly into gameplay rather than Tooie's separate launcher, a one-time **View Release / Later** notice waits for a non-combat menu scene and stays out of combat. Manual checks refresh status within the Updates tab, including offline/unavailable states. Debug and isolated import-test builds show their version and tab but do **not** offer stable updates.

**Networking change:** The update checker needs Android `INTERNET` permission for HTTPS metadata queries. Gameplay stays offline; WebView still refuses requests outside the private appassets origin. Before the GitHub repository and a signed GitHub release are public, live checks cannot reach a public stable update. If users do not have internet, gameplay and saves continue working.

## Release readiness

- [x] Confirm gamepad controls, touchscreen movement/aim and touch HUD selection on the Odin 2 Portal.
- [x] Confirm Arcane8 saves survive restarts and FrostFrontend header sizing is readable.
- [x] Implement first-run document picker, private SWF storage and game-free package verification.
- [ ] Device-test the isolated SWF import flow, cancellation/retry, gameplay and progress persistence.
- [ ] Device-test the fourth Updates tab, automatic-check toggle, offline status and L1/R1 navigation.
- [ ] Produce and verify the signed, game-free v0.2.0 APK with the permanent release keystore.
- [ ] Publish source and APK with appropriate Ruffle notices; keep original SWF/art assets out of public downloads.

**Known issue:** Rare intermittent one-sided/distorted sound was observed on the Odin. Switching Android Mono Audio on and back off temporarily corrected it, but it was not reproducible during later testing. FrostFrontend's native Left/Right/Both speaker check is available for further diagnosis; no unverified Ruffle audio processing changes are included.


## Game controls

Original keyboard/mouse: WASD movement; mouse aim and left-click attack; Space switches weapon; E throws; B swaps armour; R/T cycle throwable weapons. Sources: classic Flash game listing at flashga.me and FlashGames.BambouSoft.

The tested Odin bridge maps left stick / D-pad to WASD, right stick to aim, R1/R2 to attack, A to switch weapon, X to throw, B to armour, Y/L1 to throwable cycling. These buttons can be remapped in FrostFrontend, with unmapped controls shown explicitly. Start opens FrostFrontend.

Touch devices use a configurable joystick or digital D-pad for movement, and Relative/Direct aiming. Relative combat aim uses drag-to-aim and hold-to-attack; the game's potion/stats/options HUD buttons remain independently tappable. Five on-screen secondary-action buttons work separately from physical controller remapping. The current touch behaviour has been tested on the Odin 2 Portal.

## Publishing to GitHub

The source repository and public APK must exclude the original `arcane.swf` and unlicensed original game art. The release scripts verify this exclusion. A **game-free** APK may be shared alongside the source, provided the third-party Ruffle license/notice obligations are respected and the release makes clear that users must supply their own SWF. The repository is currently private until the owner elects to make it public.

Ruffle: https://ruffle.rs (MIT/Apache-2.0; preserve upstream notices if you redistribute its runtime).
Android WebViewAssetLoader: https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader

## Background audio and app lifecycle

The Android wrapper now explicitly suspends Ruffle (including its sounds) when the app is backgrounded and resumes on return. It also pauses/resumes WebView timers and destroys the WebView on exit. This still needs verification on the Odin: start a fight, press Home, confirm the music stops, return, then close the app from Recent Apps.

The public release build prefers the locally owned custom icon referenced by the user's preserved manifest backup. It does not commit that custom artwork to Git; the source-only fallback shield is not used in the public release script.

## Known limitations

- Browser/WebView Ruffle compatibility may vary between Android devices.
- Release-candidate audio routing can occasionally be one-sided; see the note above.
- A game-free public APK requires a user-supplied `.swf` file on first launch.
- The locally installed personal Debug launcher artwork is not present in this source-only repository. Use only original or licensed icon art in the public APK.
- Do not clear Android app storage or uninstall your existing Debug app without first securing your saves.

## Display and controller options (experimental)

**FrostFrontend** is a larger, centered pause/options overlay accessed using the Odin's **Start** button or the floating three-line icon. Opening it pauses gameplay and audio. It has persistent **Graphics**, **Controls**, **Touch Settings** and **Updates** tabs, plus a boxed **X** in the top-right corner instead of a Resume Game row. Controls contains **Button Mapping** and **Movement Settings**. Use L1/R1 to change tabs, D-pad/left stick to navigate, A to select, B to go back, and Start or X to close. Touch selection also works. Exiting Button Mapping with unmapped actions still opens the warning before leaving.

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
- Ruffle `allowNetworking: 'none'` was preventing the Flash SharedObject local-save API and FSCommand. We use `allowNetworking: 'all'` **inside Ruffle** so saving and scene notifications can function; the Android updater now holds **INTERNET permission** only for explicit HTTPS update checks. The game WebView itself still serves trusted appassets URLs and rejects external requests, so Flash network access remains blocked.
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


### v0.2.0-rc9-debug: isolated combat HUD touch and safe movement recovery

- Fixed the unwanted left/right movement/facing jerk suspected after releasing movement controls: the JavaScript neutral recovery **never synthesizes a key-down** on movement keys now. It sends `keyup` for W/A/S/D only. The native bridge still handles all actual held keys and the existing neutral recovery scheduling.
- In Relative arena mode, short taps **outside the HUD** attack using the saved crosshair without moving it. Swipes and holds still move/attack relative to the saved crosshair. In menu/results scenes and when Direct is explicitly selected, taps remain direct everywhere.
- Direct click-through in the arena is limited to the original 640x480 stage's **upper HUD band** (where its HUD and menu widgets are drawn) and the three known bottom-right **potion buttons** (original SWF HUD children `pb3`, `pb2`, `pb1` at x 508/552/596 and y 443). Coordinates are scaled for 4:3 or Stretch. Exact HUD hit regions still require a device usability check; the upper band is intentionally conservative about crosshair behavior but remains a coarse region.
- After a HUD tap, the Android pointer and Ruffle's synthetic aim are both restored to the stored relative crosshair; this avoids leaving the fighter facing the selected HUD button.
- Scene transition notifications and the verified functioning `Arcane8` save mechanism remain unchanged. This is an unverified on-device debug candidate, not a completed release.


### v0.2.0-rc10-debug: HUD routing and movement key recovery

**On-device issue:** Relative aiming and the Arcane8 save persist correctly, but combat potion/stats/options taps were not activating and the character sometimes moved without input or briefly against the requested movement direction.

- The original Flash SWF declares a 640×480 game stage. Its HUD sprite places potion children `pb3`/`pb2`/`pb1` at approximately (508,443), (552,443), (596,443), and its stats/options movieclips at approximately (205,206)/(335,207). Those are HUD-sprite local coordinates; an on-device screenshot is needed to confirm the clickable subclip positions if any hit area still misses.
- Added a **full-screen but pass-through HUD touch layer drawn above the virtual action buttons**. It intercepts only recognized HUD regions while Relative arena mode is active, leaving normal movement, aim gestures and explicit Direct mode alone. The old virtual action buttons could obscure potion hits.
- HUD input now sends a real touch DOWN when the player places a finger and an UP when they release, instead of issuing DOWN/UP immediately at the end of a tap. It restores the existing native/Ruffle relative aim after the HUD click. Other short taps continue firing at the saved crosshair.
- Gamepad movement now unconditionally releases every inactive movement key on direction changes, even if its own cache thinks the key was already released. After the controls become neutral, it resends **only** key-up events across four short, guarded passes. This is designed to recover releases that Ruffle loses while handling simultaneous game-screen touches; it never synthesises movement key-downs.
- Added `ArcaneHUD` hit/miss stage-coordinate log lines and `ArcaneGamepad` direction/recovery lines for diagnosis if either behaviour remains wrong. The proven Arcane8 save-flush and scene switching logic were not changed. Device verification is pending; this is not a public release.


### v0.2.0-rc11-debug — redesigned input delivery (not a public release)

After rc10 still let the character walk left with **no input** and didn't activate any of the game's potion, stats or options icons, the Android/WebView input paths were separated.

**Movement (single owner):** Java computes one four-bit physical/touch movement state and sends `arcaneSetMovementMask(mask)` as a whole to the browser. The browser focuses the Ruffle player and dispatches key-up/keydown changes on **the player element**, as recommended by Ruffle's own integration guidance, instead of issuing native WebView movement events plus a second JS recovery sequence. A release-only, browser-side heartbeat (220 ms) can clear any key that should be inactive without introducing accidental movement presses. `ArcaneMovementState old=X new=Y` reports the requested state; `ArcaneGamepad movement-browser-mask=X` reports Android's source. Compare those with the character if wandering recurs.

**Combat HUD (one canvas path):** The overlay's existing restricted HUD hit areas continue intercepting taps, but a new `arcaneHudPointer(kind, x, y)` sends a consistent pointer-move/down/up sequence **directly to Ruffle's shadow-DOM canvas**, with explicitly normalized `offsetX/Y` for both Original and Stretch. The native Android `WebView.dispatchTouchEvent` path was reaching pointerdown but was failing to activate the Flash HUD. `ArcaneHUDCanvas down/up at (x,y)` confirms the browser-side dispatch. Ordinary Relative arena taps remain at the existing crosshair; genuine Direct touch and arena/results scene notifications are unchanged.

**Validation performed:** JavaScript syntax parsed successfully, and a simulated browser test verified the movement sequence, release-only heartbeat, pointer-down/up order, normalized offsets and Ruffle focus. A full Android build and actual on-device gameplay **have not** been run by this assistant. Only a device test can validate whether Ruffle's input layer accepts the new events.

**Test when ready:** With the update installed over existing data, leave all inputs untouched for 15 seconds in an arena; move left, stop, move right, stop; test the virtual D-pad and joystick separately if desired; tap the visible potions, stats and in-game options; verify Relative aim and post-fight Continue/Retry; force-stop and restart once to ensure saved progress remains. Capture only `ArcaneMovementState`, `movement-browser-mask`, `ArcaneHUDCanvas`, and `ArcaneHUD` log tags if problems remain. Do not clear application data or publish this diagnostic build.


### v0.2.0-rc12-debug — protected controls and compact FrostFrontend settings

**Grounded in Odin rc11 test:** Relative aiming, keyboard movement (no wandering) and direct HUD selections now work. Remaining problems: tapping a game's potion/stats/options icon still made the fighter face/attack at that position; missing one of the D-pad buttons could start a relative aim/swipe. The user also wanted less header space in FrostFrontend and explicitly requested removal of the help/navigation row.

- The combat AimSurface now has **inert protective touch margins** around the D-pad (42 dp), joystick (30 dp), and virtual action buttons (16 dp). If a finger begins in a margin, the gesture stays inert even when dragged outside: no Relative attack, mouse movement, or synthetic movement. Actual buttons remain interactive. The HUD hit layer yields to the physical virtual-movement View if controls are swapped to the same side.
- Existing successful in-game HUD click regions are retained. An additional **guard margin** surrounds them; a near miss is absorbed instead of becoming a Relative gesture.
- HUD selection is now emitted as a **single browser operation on finger release**: Ruffle receives pointermove/pointerdown/pointerup on the HUD position, then a pointermove restoring the previous Relative aim, before a game frame can elapse between the button-down and button-up. This is intended to stop combat from interpreting a held HUD click as attack. It needs actual gameplay confirmation; if the game still attacks on the HUD pointer-down event, additional game-specific Flash-level input isolation may be necessary.
- FrostFrontend navigation now occupies **one top row**: Graphics, Controls, Touch Settings, and a reserved right-side exit X. The standalone SETTINGS heading and help/navigation text row were removed, expanding space for the scrollable settings. The controller button assignment prompt is retained where relevant.
- Single-owner Ruffle keyboard movement, saved Arcane8 progress/flush, post-fight Direct selection, and ordinary Relative attack controls were not rewritten.
- JavaScript syntax and an isolated HUD event-order test passed: pointermove/down/up/restore was verified. Java code syntax and cross-file references were inspected. This assistant has **not compiled Android or tested the actual device**. Use `test-input-rc12.ps1` after pulling main; it builds/installs in place, prompts for the 5 tests, and prints short input logs. Do not uninstall or clear the app data.


### v0.2.0-rc13-debug — FrostFrontend settings density and stereo audio investigation

The user approved a more compact settings layout and reported game sound was somewhat distorted and seemed to play out of only one speaker of the Odin 2 Portal. This version deliberately **does not rewrite the working rc12 controls, scene detector or save-flush logic**.

**Settings layout:** The menu retains Graphics / Controls / Touch Settings navigation and the close icon on a single top bar (now 38dp high). The panel has 12dp side padding. Settings are now compact, single-line, **title on the left and current value right-aligned**, with only the selected row revealing its smaller description. Each item stays a direct child of the list so existing menu selection indices, controller navigation, scrolling and immediate value changes remain intact. The help/navigation row stays removed. Unmapped controller warnings remain visible in the new layout.

**Original source audio audit:** The user's unmodified Flash 8 SWF has **38 DefineSound tags**, all MPEG Layer III / MP3, **mono**, at SWF SoundRate 1 = approximately **11.025 kHz**. This is inherently low-fidelity source material and could contribute to rough audio, but **mono should normally be audible through both speakers**. No Java WebView setting or Ruffle configuration in this repo explicitly forces mono or a left/right channel.

**Diagnostic:** Graphics now includes **Speaker Test: Left → Right → Both**. It plays a quiet 330 Hz WebAudio sine tone separately to the left, right and both channels using a StereoPannerNode, and logs `ArcaneAudioTest sampleRate=... outputChannels=... maxChannels=...`, each step, and completion/failure through `ArcaneRuffle`. It uses a separate AudioContext only while testing and **does not change** the game's Ruffle mixer or original MP3 audio. This is a diagnostic aid, not a proven fix to the sound issue.

**Interpretation:** If Left, Right and Both tones work correctly on the Odin but game audio still sounds one-sided, investigate Ruffle's Flash mono sound mixing / source decode / Android WebView audio routing; compare with the original SWF in desktop Ruffle. If the independent test is also one-sided, check the Odin's Android balance/mono audio settings, speaker functionality and WebView audio output before changing the source game.

**Validation limits:** The HTML JavaScript parses, and source-level layout and stereo-test checks were performed; no Odin run or completed Android APK compile has been performed on behalf of the user. Run `test-ui-audio-rc13.ps1` after pulling main for the in-place debug APK build, install and guided audio/UI test; do not uninstall or clear data.
