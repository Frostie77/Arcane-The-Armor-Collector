# Arcane: The Armor Collector - Android v0.2.0

> Draft release notes. Publish after the game-free import test and signed APK verification pass.

An unofficial Android wrapper for the original Flash game, built with Ruffle and Android WebView. Not affiliated with the original game's creators or the Ruffle project.

## What's included

- Native Android landscape gameplay, with offline Ruffle playback.
- AYN Odin 2 Portal gamepad controls and configurable button mapping.
- Touchscreen joystick or D-pad, Relative/Direct aiming and touch action controls.
- Tap access to the game's potion, stats and options HUD without unintended aiming.
- FrostFrontend settings: Graphics, Controls, Touch Settings and a compact menu layout.
- Original 4:3 or Stretch to Screen display.
- Persistent settings and Arcane8 Flash save data.
- Native speaker check (Left / Right / Both) for troubleshooting.
- **Updates** heading in FrostFrontend: installed/latest version, manual check, automatic checks on launch, and a verified GitHub release page link. Notices appear when in menus, not combat.
- Custom launcher icon created for the Android port.

## First launch: import your own game file

**This APK does not contain the original game.** Copy your legally obtained Arcane: The Armor Collector Flash `.swf` file onto your Android device. Start the APK, select **Select Game File**, and choose the file using Android's document picker. The app copies it into its private storage. You only need to import it once.

### Where to get Arcane.swf

The original game was uploaded by Hadar1989 to Newgrounds. Obtain it directly from the original game's host rather than from this repository:

- **[Download original Arcane SWF (Newgrounds)](https://uploads.ungrounded.net/442000/442706_Arcane.swf)**
- [Original Arcane game page and author credits](https://www.newgrounds.com/portal/view/442706)

Save the linked `.swf` file to your device's **Downloads** folder and select it from the app's first-run file picker. If the browser opens the file instead of saving it, use **Save link as...**. No renaming is required; the import supports the original `442706_Arcane.swf` filename. Game content remains owned by its respective rights holders.

No all-files storage permission is requested. The original Flash game runs offline; the updater uses internet access only for optional HTTPS update metadata and a browser-based release link.

## Known issues

Intermittent distorted/one-sided game sound was previously observed on one Odin 2 Portal. The cause has not yet been isolated; it could involve the Ruffle-to-WebView-to-Android audio path. A native stereo speaker diagnostic is included.

## Existing private Debug users

Release signing differs from Android Debug signing. **Do not uninstall your existing Debug app to install this release without making a plan for your saved progress.** The separate `.importtest` build can test the file-import process without touching your existing app or saves.

## Distribution and attribution

The original SWF, game artwork and other game-owned assets are **not** part of this download. Supply your own lawfully obtained game file.

Ruffle: https://ruffle.rs - used under its published MIT/Apache-2.0 licensing terms. Preserve the upstream license files and notices when redistributing its runtime.

**Important:** v0.2.0 is the initial signed release. Its update metadata is staged from the signed APK and published only after the matching public GitHub Release exists. Users can update future versions over the signed release without removing game files or saves, provided every release retains the same Android package and signing certificate.

This project is unofficial and provided without any affiliation or endorsement by the original game's rights holders.
