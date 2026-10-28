# Lock Gestures — Ubuntu Touch version (handover plan)

This file lets a separate Claude chat (or anyone) build the Ubuntu Touch version of Lock Gestures
without the history of the Android chat. Start the new chat with:

> Build the Ubuntu Touch version of my Lock Gestures app. Read `docs/UBUNTU_TOUCH_PLAN.md`
> in https://github.com/lewisw445/lockscreen-gestures and follow it.

## Decisions already made

| Question | Answer |
|---|---|
| Keep the Android version? | Yes. Ubuntu Touch code lives in `ubuntu-touch/` in this repo; `app/` (Android) is untouched. |
| Target device | Nothing Phone (1) ("Spacewar"), community port by Nonta72 |
| OS version | Ubuntu Touch **24.04** (Lomiri) |
| Lock-screen patch that must be re-applied after OTA updates | **Accepted** |
| Developer's laptop | **Windows 11** |
| Single screen | No fold/cover-screen logic (drop `onlyWhenFolded`, `DeviceState.isFolded`) |

## What the app must do (feature parity with Android)

UI (same layout as Android — see `app/src/main/java/com/lewisw/lockgestures/ui/`):

1. **Zones card** — mini preview of the screen with zones, **Set** (full-screen editor) and **Clear**
   (wipes zones *and* functions, with confirmation).
2. **Zone editor** — add / drag / resize / delete rectangles. **Zones must never overlap**
   (port `model/ZoneLayout.kt`: moves slide along neighbours, resizes stop at them, Add finds free space).
   Status-bar and bottom gesture-bar areas are off limits.
3. **Functions card** — empty by default with an **Add function** button. Each row: Trigger · Zone · Action
   dropdowns + remove. Only valid combinations are offered (`SourceKind.validTriggers()`).
4. Lock-screen zone outlines show the **icons of the linked actions**, not numbers.
5. **Draw Shape trigger** — the user records a shape 3× (up to 5) in a dialog; drawing that shape inside
   the zone on the lock screen runs the action. Port `model/Shapes.kt` ($P point-cloud recogniser,
   48 points, base thresholds 0.11 / 0.09 / 0.075 loose/normal/strict, raised to the worst distance between the user's own samples (capped at base × 1.2) — keep in sync with the Android values).
   Show the ink trail while drawing. Warn when a new shape looks like another shape on the same zone.
6. Settings: enable switch, only-on-lock-screen, show outlines, motion sensitivity, shape matching strictness.
7. "Last gesture" line showing the most recent trigger and its result.

Triggers: Single Tap, Double Tap, Swipe Up/Right/Left/Down, Press + Hold, Draw Shape (zones only);
Volume Up/Down keys: single press, double press, press + hold; Gyro Up/Down/Left/Right/Back/Forth: flick, double flick.

Touch zones must work on the **lock screen only**, not on the PIN/passcode entry screen.

## Architecture on Ubuntu Touch

Normal Ubuntu Touch apps are AppArmor-confined and suspended in the background, so three parts are needed:

| Part | Tech | Job |
|---|---|---|
| `ubuntu-touch/app/` | QML + C++ (Qt 5, Lomiri components), `.click`, **unconfined** | Settings UI. Writes config JSON to `~/.config/lockgestures/config.json` (same schema as Android `Config.toJson`). |
| `ubuntu-touch/daemon/` | C++/Qt (or Python) systemd **user** service | Watches volume keys (evdev on `/dev/input/event*` — may need a udev rule or root helper), motion (QtSensors), runs actions. Exposes a small D-Bus API (`org.lewisw.LockGestures`) that the lock-screen patch calls when a zone fires. |
| `ubuntu-touch/greeter-patch/` | QML patch for Lomiri's greeter | Draws the zones (outline + icons + ink) over the lock screen, recognises taps/swipes/hold/drawn shapes, hides them while the passcode screen is showing, calls the daemon over D-Bus. |

Install/repair script (`ubuntu-touch/install.sh`, run over SSH or in the terminal app):
remount `/` read-write, back up and patch Lomiri's greeter QML, install the daemon + systemd unit,
remount read-only, restart Lomiri. Must be **idempotent** and re-runnable after every OTA update, and include
`--uninstall` that restores the backup.

### Action mapping (verify each on the device before relying on it)

| Action | Likely Ubuntu Touch route |
|---|---|
| Torch | flashlight LED in sysfs (as used by the battery indicator's flashlight toggle) |
| Airplane mode | Lomiri connectivity / indicator-network D-Bus (`FlightMode`) or ofono `Online` |
| Wi-Fi | NetworkManager D-Bus `WirelessEnabled` |
| Bluetooth | BlueZ D-Bus adapter `Powered` |
| Hotspot | Lomiri connectivity D-Bus hotspot properties, or NetworkManager AP connection |
| Brightness up/down | repowerd / powerd D-Bus or sysfs backlight |
| Volume up/down | PulseAudio (`pactl` / libpulse) |
| Silent mode | AccountsService sound setting (SilentMode) |
| Open app | `lomiri-app-launch` / `ubuntu-app-launch` (after unlock) |
| Close app / Close all | `lomiri-app-launch` stop (after unlock) |
| NFC | **Not supported on Ubuntu Touch** — hide from the list |
| Battery saver | No known system toggle — research; hide if none |

Radio toggles run directly, so unlike Android there is no Quick Settings tapping and no unlock is needed.

### Known device issues (from the UBports forum, Oct 2026)

Fingerprint reader doesn't work; vibration motor is buggy (haptic confirmation may be unreliable);
SIM 1 not detected on some units.

## Developer setup on Windows 11

1. Install **WSL 2** with Ubuntu 24.04 (`wsl --install -d Ubuntu-24.04`).
2. Install **Docker Desktop** with the WSL 2 backend enabled.
3. In WSL: `pip install clickable-ut` (or follow the Clickable docs), then `clickable desktop` inside
   `ubuntu-touch/app` to preview the settings UI on the laptop.
4. Phone: enable Developer Mode (Settings → About → Developer Mode), connect over USB, then
   `clickable` builds and installs the `.click` onto the phone. The daemon and greeter patch can only be
   tested on the phone itself.

## CI

Add a GitHub Actions job that builds the `.click` with the Clickable Docker image for `arm64` and attaches it,
plus `install.sh`, to each release next to the Android APK.

## Suggested build order

1. Settings app UI + config JSON (testable on the laptop).
2. Daemon with actions, triggered from a debug button in the app.
3. Volume keys + gyro in the daemon.
4. Greeter patch: zones, icons, taps/swipes/hold.
5. Draw Shape on the lock screen.
6. Passcode-screen detection, install/repair/uninstall script, CI.
