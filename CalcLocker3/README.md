# Calculator (Calc Locker)

A fully working calculator that doubles as an app vault. Type your secret 6-digit code,
press `=`, and the app you chose (e.g. WhatsApp) opens. The code never persists — it only
ever lives on the calculator display and is wiped the moment it's used.

## What changed in this version

- **The app *is* the calculator now.** No more watching the system Calculator — that was
  fragile across devices. This one does real arithmetic (`+ − × ÷ %`, sign toggle, decimals)
  and recognises the code internally.
- **Code is ephemeral.** On a correct code we launch the target app and immediately reset the
  display to `0`. Nothing about the entered code is stored.
- **Optional enforcement.** An accessibility service can still block the locked app if it's
  opened directly from its icon. It now only checks the *foreground package name* — it reads
  no screen content.

## How a user experiences it

1. First launch → setup: pick the app to lock, set a 6-digit code, "You're all set!".
2. After that it always opens as a plain calculator.
3. Type the code, press `=` → the locked app opens.
4. To reach settings again: **long-press `AC`**, enter your code.

## Build & run

Open the `CalcLocker` folder in Android Studio, let it sync, press Run. Or:

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## About hiding WhatsApp from the launcher — the honest answer

**A normal Play Store app cannot hide another installed app's icon from the launcher while
keeping that app launchable.** Android isolates apps on purpose; one app can't enable/disable
another app's launcher entry. The only mechanisms that *can* hide an arbitrary app, and why
none fit "sell a calculator on the Play Store":

- **Device Owner / MDM** (`DevicePolicyManager.setApplicationHidden`): genuinely hides any app,
  but (a) it requires enterprise provisioning on a *freshly reset* device via `adb`/QR/NFC — a
  consumer can't grant it to a downloaded app — and (b) a "hidden" app is also *suspended*, so
  it won't run until you unhide it, which makes it reappear. So it can't give you
  "invisible icon but still openable via the calculator."
- **Being the home launcher**: if your app replaces the user's launcher, *you* decide what
  shows, so you can omit WhatsApp. Works, no special rights, but it means shipping a full home
  launcher and asking every user to switch to it — wrong product for a calculator.
- **Root / Xposed**: can freeze/hide apps, but that's not something you can ship on Play.
- **App cloning / virtualization** (Parallel Space–style): you run a *clone* of WhatsApp inside
  a container and hide the original. Heavy, brittle, breaks logins/notifications, and Play has
  tightened the screws on this category too.

**What this project does instead** to reach the same *security* goal: the optional accessibility
lock blocks WhatsApp whenever it's opened from its icon, pushing the user back to the calculator.
The icon stays visible, but it's useless without the code. That's the realistic consumer-app
equivalent of "only accessible via the calculator."

## About selling on Google Play — read before investing

- **Disguised-calculator vault apps are a gray area.** Many exist, but Google has removed waves
  of them for "deceptive behavior" / hiding their true purpose. Approval is not guaranteed.
- **The accessibility-based app lock is the riskiest part.** Play's Accessibility API policy
  requires an accessibility purpose; app-lockers get flagged and increasingly rejected. If you
  ship the lock, expect to justify it or get pushback.
- **`QUERY_ALL_PACKAGES`** needs a declared, approved use or Play will reject it.

**The version most likely to actually pass review and sell** is a self-contained *private vault*:
the calculator unlocks your own photos / notes / files *inside the app*. You're only ever hiding
your own content, so there's no other-app manipulation, no accessibility service, and "hiding"
genuinely works because it's your data. If you want, that's a straightforward redirection of this
same calculator front-end — happy to build it.

## Files

| File | Role |
|------|------|
| `CalculatorActivity.kt` | The calculator UI + hidden code listener + settings gate |
| `CalcEngine.kt` | Expression evaluator |
| `SetupAndSettings.kt` | Setup flow, app picker, code entry, settings |
| `UnlockGate.kt` | Unlock signal shared with the guard service |
| `service/LockerAccessibilityService.kt` | Optional: blocks the app opened from its icon |
| `lock/LockScreenActivity.kt` | The block screen |
| `data/LockerPrefs.kt` | Target app + salted hash of the code |
