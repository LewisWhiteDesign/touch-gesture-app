# Lock Gestures

Run quick actions from the Galaxy Z Fold 7's **cover-screen lock screen** without unlocking,
using tap/swipe zones you draw, the volume keys, or flicks of the phone.

## How it's organised

- **Zones** – a mini preview of the cover screen. **Set** opens a full-screen editor where you add,
  move and resize touch zones. **Clear** removes all zones and functions.
- **Functions** – rows of *Trigger · Zone · Action*. Tap **Add function** to create one.

| Trigger | Zone | Action |
|---|---|---|
| Single/Double Tap, Swipe Up/Down/Left/Right, Press + Hold, **Draw Shape** | Zone 1…n | Torch, Brightness ±, Silent mode — done directly |
| Single/Double Press, Press + Hold | Volume Up / Down | Wi-Fi, Bluetooth, Airplane, Hotspot, NFC, Battery saver — taps the Quick Settings tile |
| Flick / Double Flick | Gyro Up/Down/Left/Right/Back/Forth | Open app, Close app, Close all apps — asks for PIN first |

Everything runs inside this one app via an Accessibility Service (the only way an app may draw
over the lock screen and read the volume keys).

**Draw Shape:** pick it as the trigger, then draw your shape 3 times in the recorder. Drawing that
shape inside the zone on the lock screen runs the action. Zones can't overlap in the editor.

**Ubuntu Touch:** see [`docs/UBUNTU_TOUCH_PLAN.md`](docs/UBUNTU_TOUCH_PLAN.md).

## Install on the Fold 7

1. Turn off **Auto Blocker** (Settings → Security and privacy).
2. Download the latest `LockGestures-*.apk` from the repo's **Releases** page and install it.
3. App info → ⋮ → **Allow restricted settings**.
4. Settings → Accessibility → Installed apps → **Lock Gestures** → on.
5. In the app's Settings screen, grant Silent mode and Brightness access if you use those actions.
6. Settings → Lock screen and AOD → Secure lock settings → turn off **Lock network and security**.
7. Put the Quick Settings tiles you use on the first page of the panel.

## Emulator (Android Studio)

1. Clone https://github.com/LewisWhiteDesign/touch-gesture-app and open it in Android Studio.
2. Device Manager → **7.6" Fold-in with outer display**, Android 16 (API 36). Fold it.
3. Run, then enable the service under Settings → Accessibility on the emulator.
4. In the app's Settings, turn off **Only on the lock screen** to test while unlocked.
5. Volume keys: emulator side toolbar. Motion: ⋮ → **Virtual sensors**.
   The emulator has no flash, so Torch reports "no flash on this device".
