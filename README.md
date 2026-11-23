# Roundly

Calm, precise interval alarms for Android phones and Wear OS. Fully offline: no account, no
internet, no analytics. Everything stays on the device.

- **Fixed interval:** every X h Y min, anchored so it never drifts.
- **Irregular:** N alerts per window at unpredictable (`SecureRandom`) times. The times are never shown.
- **Stepped:** an ordered list of `interval × repeats` steps, then stop or keep the last interval.
- **One-off timer:** a countdown or a clock time, with Recent timers and one-tap restart.
- **Wear OS companion:** alerts, toggles, Pause all, a tile and a complication. It keeps alerting
  on its own when the phone is out of range.

---

## Project layout

| Module | What it contains |
|---|---|
| `:core` | Pure Kotlin, no Android dependencies. Models, `ScheduleCalculator`, validation, `AlarmFactory`, haptic waveforms, backup codec, and the phone–watch sync contract. Fully unit tested. |
| `:app` | Phone app. Room, DataStore, Hilt, the scheduler (`setAlarmClock`), receivers, the short-lived ringing service, notifications, the Compose UI and the Data Layer bridge. |
| `:wear` | Wear OS app. The synced schedule copy, standalone fallback scheduler, full-screen alert and haptics, alarm list, tile and complication. |

Architecture: MVVM with repositories, `StateFlow` and coroutines. All scheduling decisions are
made by the pure `ScheduleCalculator`; Android classes only persist the result and arm one
`AlarmManager` trigger per alarm. No service runs between alerts.

## Building

Requirements: Android Studio with AGP 9.5, JDK 17+, and Android SDK platform 37 installed.

```bash
./gradlew :core:test                                # 68 unit tests for the scheduling engine
./gradlew :app:assembleDebug :wear:assembleDebug    # debug APKs
./gradlew :app:lintRelease :wear:lintRelease        # lint (currently: no issues)
./gradlew :app:bundleRelease :wear:bundleRelease    # AABs for Play
```

### Release signing (important for the watch)

The phone and watch apps **must** have the same `applicationId` (`app.roundly`) **and be signed
with the same key**. The Wearable Data Layer only connects apps that match on both.
Configure a `signingConfig` (or use Play App Signing with the same upload key) for both modules.

Version codes: the phone uses `1, 2, 3…`, the watch uses `1_000_001, 1_000_002…`, so both AABs
can live in the same Play listing without clashing.

### Database migrations

Room exports every schema version to `app/schemas/`. Commit that folder. There is no
destructive fallback on upgrade (see `data/db/Migrations.kt`): add an `@AutoMigration` for
additive changes, or a tested hand-written `Migration` for anything else.

---

## Privacy and offline guarantees

- Neither the phone nor the watch app declares `INTERNET` or `ACCESS_NETWORK_STATE`. Both
  manifests also strip them with `tools:node="remove"`, so no library can add them.
  Verified in the merged debug **and release** manifests:
  ```bash
  grep -c -E 'permission.INTERNET|ACCESS_NETWORK_STATE' \
    app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml \
    wear/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
  # both print 0
  ```
- Phone–watch traffic uses the Wearable Data Layer over the Bluetooth link between paired
  devices (`DataClient` for state and queued watch responses, `MessageClient` for live alerts).
- `android:allowBackup="false"`, and `data_extraction_rules.xml` also excludes everything from
  **device-to-device transfer**. On Android 12+, `allowBackup=false` alone does not stop that.
- The only way data leaves the device is a manual **Settings › Export** (Storage Access
  Framework). Exports never contain the hidden irregular times.
- No ads, analytics, crash reporting, Firebase or accounts.

---

## Google Play Console declarations

Complete these under **Policy and programs › App content** (some only appear after you upload a
bundle that uses the permission).

### 1. Exact alarms (`USE_EXACT_ALARM`)

Roundly declares `USE_EXACT_ALARM` (API 33+) and `SCHEDULE_EXACT_ALARM` with
`maxSdkVersion="32"`. `USE_EXACT_ALARM` is restricted to apps whose **core function is an alarm
clock, timer or calendar**. Roundly qualifies: its whole purpose is user-set interval alarms and
timers.

Suggested declaration text:

> Roundly is an interval alarm clock and timer app. Every alarm the user creates (fixed
> intervals, irregular reminders, stepped schedules and one-off timers) must ring at the exact
> time chosen, including in Doze. Roundly uses `AlarmManager.setAlarmClock()` for each user-set
> alarm, schedules only the single next alarm per user alarm, and never schedules alarms on its
> own.

### 2. Full-screen intent (`USE_FULL_SCREEN_INTENT`)

On Android 14+, this permission is only granted by default to calling and alarm apps.
Choose the **"Alarm"** use case.

> When a user-set alarm or timer goes off, Roundly shows a full-screen alert over the lock
> screen with Done and Snooze buttons, like a clock app's alarm. If the permission is turned off,
> Roundly falls back to a heads-up notification with the same actions and links the user to the
> system setting.

### 3. Foreground service (`FOREGROUND_SERVICE_SYSTEM_EXEMPTED`)

- **Service:** `app.roundly.ringing.RingingService`, `foregroundServiceType="systemExempted"`.
- **Why this type:** Android reserves `systemExempted` for, among others, alarm clock apps that
  hold `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` and use a foreground service to keep delivering
  alarms, including haptics-only alarms. Roundly's prerequisite is `USE_EXACT_ALARM` (API 33+),
  which is granted at install. `mediaPlayback` would not fit the vibrate-only and silent modes,
  which play no media.
- **When it runs:** only while an alert is actually ringing, started from the exact-alarm
  broadcast. It stops on Done, Snooze or the ring timeout (default 3 min). It never runs between
  alerts, at boot, or in the background otherwise.
- **What it does:** plays the alarm sound (`AudioAttributes.USAGE_ALARM`), the vibration pattern
  and the discreet-escalation sequence, and enforces the ring timeout.
- **User impact if deferred:** the alarm would not sound, defeating the app's core purpose.
- Play asks for a **short video** showing the feature. Record an alarm firing on a locked phone,
  the alert screen appearing, and Done stopping it.

Suggested declaration text:

> Roundly is an alarm clock and timer app that holds `USE_EXACT_ALARM`. When a user-set alarm
> fires, a foreground service runs only while that alarm is ringing, to play the alarm sound,
> vibration and escalation pattern (some alarm modes are vibration-only), and to stop after the
> user's ring timeout. It stops as soon as the user taps Done or Snooze.

### 4. Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (no data collected) |
| Do you provide a way for users to request that their data is deleted? | Not applicable. All data is local; uninstalling the app or clearing its storage removes it. |

Roundly has no network permission. Alarm data stays on the device, and phone–watch sync is
device-to-device over Bluetooth, which the Data safety form does not count as "collection".

### 5. Privacy policy

Play Console asks for a privacy policy URL. Host a short page such as:

> **Roundly privacy policy.** Roundly does not collect, store remotely, share or sell any
> personal data. The app has no internet access. Your alarms, presets and settings are stored
> only on your phone and, if you use the Wear OS app, synced directly to your paired watch over
> Bluetooth. Nothing is backed up to the cloud. You can export your data to a file of your
> choice from Settings; that file is under your control. Contact: *your email*.

### 6. Other App content forms

- **Ads:** No ads.
- **App access:** All functionality is available without login.
- **Target audience:** 13+ (or 18+), not designed for children.
- **Content rating:** complete the questionnaire (no user-generated content, no sharing, no location).
- **News, government, financial, health declarations:** not applicable.

### 7. Target API level

| | Required by Google Play (as of Aug 31, 2026) | Roundly |
|---|---|---|
| Phone | Android 16 (API 36) | `targetSdk 36`, `compileSdk 37` |
| Wear OS | Android 15 (API 35) | `targetSdk 36` |

Lint's "not targeting the latest" check is disabled deliberately: 37 is available but not yet
required. Raise `targetSdk` when Play announces the next requirement.

### 8. Wear OS listing

1. **Release › Advanced settings › Form factors:** add **Wear OS**, then upload the `:wear`
   bundle to the Wear OS track (or the same track as the phone).
2. The watch app is a companion (`com.google.android.wearable.standalone=false`): alarms are
   created on the phone.
3. Provide Wear OS screenshots (round, 384×384 or larger), covering the alarm list, alert, tile
   and complication. Expect a separate Wear OS quality review.
4. The watch app contains no 32-bit-only native code. Its two small AndroidX libraries ship for
   `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`, so it meets the 64-bit requirement.

---

## Permissions and why each is needed

| Permission | Phone | Watch | Why |
|---|---|---|---|
| `POST_NOTIFICATIONS` | ✓ | ✓ | Alert, Missed and Finished notifications (runtime on API 33+). |
| `USE_EXACT_ALARM` | ✓ | ✓ | Exact user-set alarms on API 33+. |
| `SCHEDULE_EXACT_ALARM` (max 32) | ✓ | ✓ | The same on API 31–32 only. |
| `USE_FULL_SCREEN_INTENT` | ✓ | ✓ | Alarm screen over the lock screen. |
| `FOREGROUND_SERVICE`, `…_SYSTEM_EXEMPTED` | ✓ | – | Ringing service, only while an alert rings (alarm-app use of `systemExempted`). |
| `RECEIVE_BOOT_COMPLETED` | ✓ | ✓ | Re-arm alarms after a reboot. |
| `VIBRATE` | ✓ | ✓ | Alert haptics. |
| `WAKE_LOCK` | ✓ | ✓ | Keep the CPU awake while an alert is ringing (bounded by the ring timeout). |

**Not used:** `INTERNET`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (the
battery card only opens the system list screen), and any location, contacts or storage permission.

---

## Platform behaviour review (Android 12–16)

| Change | How Roundly handles it |
|---|---|
| Exact alarms need permission (12+); `SCHEDULE_EXACT_ALARM` denied by default (14+) | `USE_EXACT_ALARM` on 33+; checks `canScheduleExactAlarms()`; handles `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`; falls back to an inexact alarm and shows a warning. |
| Doze throttles `setExactAndAllowWhileIdle` | Never used for alerts; every alert uses `setAlarmClock()`. |
| Foreground service types required (14) | `systemExempted` declared with its permission; its prerequisite (`USE_EXACT_ALARM`) is held (see §3). The type is passed to `startForeground()` on 14+ only. |
| Background FGS start limits (12+, tightened in 15) | Started only from the exact-alarm broadcast, which is exempt. If it is still refused, the alert is posted as a heads-up notification with actions. |
| Foreground services started from `BOOT_COMPLETED` are restricted (15) | Never done; boot only re-arms alarms. |
| Full-screen intent limited to alarm/calling apps (14) | Checks `canUseFullScreenIntent()`; heads-up fallback plus a dashboard banner linking to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. |
| Audio focus only for foreground or FGS apps (15) | Focus is requested from inside the running ringing service. |
| Notification permission (13) | Asked in onboarding; Permissions health card; persistent dashboard banner. |
| Context-registered receivers need an export flag (14) | `RECEIVER_NOT_EXPORTED` for the screen-off (power key) receiver. |
| PendingIntent mutability and safer intents (12, 14) | All PendingIntents are explicit and `FLAG_IMMUTABLE`. |
| Edge-to-edge enforced (15) | `enableEdgeToEdge()`; every screen applies system-bar insets. |
| Predictive back (16 with target 36) | No legacy `onBackPressed`; Navigation Compose handles back. |
| `setShowWhenLocked`/`setTurnScreenOn` are API 27+ | API 26 uses the window-flag fallback (found and fixed in this pass). |
| 16 KB memory pages (Play requirement when targeting 15+) | Native libs (AndroidX graphics-path, DataStore) have 16 KB-aligned ELF segments; `zipalign -c -P 16` passes for both release APKs. |
| Cloud backup and device-to-device transfer | Both disabled (see Privacy). |

---

## Known follow-ups

- **Test on hardware:** a phone plus a watch, locked screen, Doze (`adb shell dumpsys deviceidle force-idle`),
  reboot, time-zone change, and OEM battery savers (Samsung, Xiaomi, OnePlus, Huawei).
- **Compose BOM:** 2026.11.01 needs `compileSdk 37.1`. Install that platform, then bump `composeBom`
  and remove the `compose-bom` ignore in `app/lint.xml` and `wear/lint.xml`.
- **Instrumented tests:** add Room DAO and `MigrationTestHelper` tests in `app/src/androidTest`
  before the first schema change.
