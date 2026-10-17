# Lock Gestures

Trigger quick actions from the Galaxy Z Fold 7's **cover screen lock screen** without unlocking.

**Current features**
- Shake twice sharply → toggle the torch (with a vibration to confirm: one buzz = on, two = off)
- Works only when folded and locked (both can be switched off for testing)

**Planned:** taps/swipes on the cover lock screen, airplane mode, hotspot (via Shizuku).

## Getting the APK

Every push to `main` builds automatically. Open the repo's **Releases** page on your phone,
download the latest `LockGestures-*.apk`, and install it. Updates install over the top.

Before installing on the Fold 7:
1. Turn off **Auto Blocker** (Settings → Security and privacy → Auto Blocker).
2. Allow your browser/file manager to **install unknown apps**.

After installing, open the app and:
1. Turn on **Enable lock-screen gestures**.
2. Tap **Allow notifications** and **Let it run in the background**.
3. Settings → Battery → Background usage limits → **Never sleeping apps** → add Lock Gestures.

## Running in the emulator (Android Studio)

1. `git clone https://github.com/lewisw445/lockscreen-gestures` and open the folder in Android Studio.
2. Device Manager → create a device using **7.6" Fold-in with outer display**, Android 16 (API 36).
3. Press ▶ Run. Fold the emulated device (toolbar → fold button) so it uses the outer screen.
4. In the app, enable gestures. To test without locking, switch off **Only on the lock screen**.
5. Simulate a shake: emulator `⋮` → **Virtual sensors** → drag the phone model quickly back and forth,
   or from a terminal:

   ```
   adb emu sensor set acceleration 25:0:9.8
   adb emu sensor set acceleration 0:0:9.8
   adb emu sensor set acceleration -25:0:9.8
   adb emu sensor set acceleration 0:0:9.8
   ```

   The emulator has no flash, so the app will report "no flash on this device" — that still
   proves the shake was detected. The **Last gesture** line in the app and the notification update.
