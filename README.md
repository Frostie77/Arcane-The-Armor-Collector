# Arcane: The Armor Collector — Android

An unofficial Android version of the classic Flash game **Arcane: The Armor Collector**, powered by [Ruffle](https://ruffle.rs). Play with a gamepad or touchscreen, with a custom settings interface called **FrostFrontend**.

Developed and tested on the **AYN Odin 2 Portal**. Other Android devices running **Android 8.0 or newer** may also work.

> **The original game is not included in the APK.** You'll need to provide your own Flash `.swf` file.

## Download and installation

1. Download **Arcane-v0.2.0-Android.apk** from [GitHub Releases](https://github.com/Frostie77/Arcane-The-Armor-Collector/releases).
2. Install and open the APK on your Android device.
3. Download the [original Arcane SWF from Newgrounds](https://uploads.ungrounded.net/442000/442706_Arcane.swf), or use your own legally obtained copy.
4. Tap **Select Game File** and choose the SWF from your Downloads folder.
5. Start playing. The app keeps its own private copy, so you only need to import the file once.

You can also visit the [original game page on Newgrounds](https://www.newgrounds.com/portal/view/442706). If your browser opens the SWF instead of downloading it, use **Save link as**. There's no need to rename the file.

## Introducing FrostFrontend

**FrostFrontend** is my custom Android settings interface, built to make Arcane comfortable to play on handhelds and touchscreens.

Open it with the **Start** button on a controller or the **three-line menu icon** on-screen. The game pauses while the menu is open.

| Section | Features |
| --- | --- |
| **Graphics** | Original 4:3 or stretched widescreen display, plus a left/right speaker test. |
| **Controls** | Button remapping, aiming sensitivity, inverted aiming and movement settings. |
| **Touch Settings** | On-screen joystick or D-pad, relative/direct aiming, size, opacity and positioning. |
| **Updates** | Installed version, manual update check and optional automatic checks. |

Use **L1/R1** to move between tabs, **A** to select and **B** to go back. Touchscreen navigation works too.

Updates are checked in the background. If a newer verified public version exists, FrostFrontend offers a link to its GitHub release page. **It never downloads or installs an APK without you choosing to do so.**

## Default gamepad controls

| Button | Action |
| --- | --- |
| Left stick / D-pad | Move |
| Right stick | Aim |
| R1 / R2 | Attack |
| A | Switch weapon |
| B | Swap armour |
| X | Throw |
| Y / L1 | Cycle throwables |
| Start | Open FrostFrontend |

You can change the button mapping in **FrostFrontend → Controls**. Touchscreen players can use the virtual movement and action controls.

## Saves and updates

The app keeps your game progress and settings in its own Android app storage. Future signed release updates should install over the existing public APK without removing that data.

**Do not uninstall the app or clear its storage unless you've backed up anything you need.** Private Debug builds have a different signing certificate and cannot be directly replaced by the public release APK.

Gameplay works offline. Internet access is only used for optional update checks and opening the GitHub release page.

## Known issue

Some testing on the Odin 2 Portal revealed occasional distorted or uneven left/right game audio. **FrostFrontend → Graphics → Speaker Test** can help check your device's audio output.

## Building from source

For developers on Windows, install **JDK 17** and the **Android SDK**, then run `prepare.ps1` to download the required Ruffle runtime and Gradle tools. Build with Gradle in the project folder. Your own game SWF can be imported when the app starts; it is not needed inside the APK.

The included release scripts are intended for this project's release maintainer and require a separate, protected signing key. Do not commit signing keys or game files.

## Credits and disclaimer

- **Original game:** *Arcane: The Armor Collector* — [Newgrounds original game page](https://www.newgrounds.com/portal/view/442706)
- **Flash compatibility:** [Ruffle](https://ruffle.rs) (MIT/Apache-2.0)
- **Android wrapper, controls and FrostFrontend:** This unofficial project

This project is not affiliated with or endorsed by the original game developers or Ruffle. The game, its artwork and music remain the property of their respective owners. **The original SWF is not bundled or redistributed by this repository.**
