# Role

You are a senior Android engineer and product designer working inside my Android Studio project. Build a production-quality, battery-efficient **interval alarm app** for phone and Wear OS that can pass Google Play review. The app should look **minimalist and elegant**. Create and edit the actual project files. Don't just print code.

# Non-negotiables

- **Fully offline.** Do not declare the `INTERNET` permission in either module. No accounts, login, sign-up, cloud sync, ads, analytics, crash-reporting SDKs or Firebase.
- All data stays on the device. Phone↔watch communication uses the Wearable Data Layer over Bluetooth only.
- Set `android:allowBackup="false"` so nothing leaves the device through cloud backup. Provide a manual **Export / Import (JSON)** in Settings using the Storage Access Framework.
- This keeps the Play Data Safety form at "No data collected or shared".

# Tech stack and project setup

- Kotlin, Jetpack Compose with Material 3. Phone module `:app`, watch module `:wear`, shared pure-Kotlin module `:core` (models, `ScheduleCalculator`, serialisation).
- Gradle Kotlin DSL with a version catalog (`libs.versions.toml`)
- Phone minSdk 26. Wear minSdk 30. compileSdk and targetSdk set to the latest level Google Play currently requires (check this; use API 36 if unsure).
- Room (via KSP), DataStore (Preferences), Hilt, Navigation Compose, kotlinx.serialization
- MVVM with the Repository pattern, `StateFlow`, coroutines

# Design direction: minimalist but elegant

- **Calm and uncluttered:** generous whitespace, a clear typographic hierarchy, and content over chrome. At most one primary action visible per screen.
- **Colour:** neutral surfaces with a single refined accent colour (e.g. deep teal). Material You dynamic colour is an optional setting, off by default, so the designed palette shows first. No gradients and no heavy shadows. Use tonal elevation only.
- **Typography:** the system font, or one bundled variable font. Show countdowns and times in large **tabular numerals** so digits don't shift as they change. Use two or three text sizes per screen at most.
- **Shapes and motion:** 20–24 dp rounded cards, thin dividers or none, and subtle 150–250 ms transitions (`animateContentSize`, crossfades). Nothing bouncy.
- **Dark mode is first-class**, with a true-dark option for night use: near-black background and dimmed accent.
- **Alert screen:** full-bleed and calm. The label is large and centred, the time is beneath it, and the two big buttons sit at the bottom in the thumb zone. The night palette has no harsh white flash.
- **Dashboard:** the next upcoming alert is shown as a large "hero" countdown at the top, with the alarm list beneath it.
- Provide Compose `@Preview`s (light and dark) for every main screen.

# Core features (phone)

## 1. Alarm types

Each alarm has a **label**, an **alert mode** and an optional **run for** limit. There are four types:

1. **Fixed interval:** repeats every X hours and minutes (1 min to 23 h 59 min).
2. **Irregular interval:** N alerts per time window at **unpredictable times**, e.g. "4 times per hour".
   - User settings: window length (e.g. 60 min), alerts per window (1–12), and minimum gap between alerts (default 5 min).
   - For each window, generate the times with `SecureRandom`. Respect the minimum gap and keep every alert inside its window. Reject impossible combinations in the editor with a clear message (e.g. 12 alerts in 30 min with a 5 min gap).
   - The UI shows only "Next alert within this window". It **never reveals upcoming random times**, so the pattern can't be predicted. Schedule only the next one.
3. **Stepped schedule:** an ordered list of steps, each with an **interval × repeat count**.
   - Example: every 15 min ×4, then every 30 min ×2, then hourly ×4.
   - After the last step, the alarm either **stops** or **continues at the last interval** (user choice).
   - The editor is a simple reorderable list of step rows with add/remove.
   - The dashboard and alert screen show progress, e.g. "Step 2 of 3 · alert 1 of 2".
   - Users can save stepped schedules as presets.
4. **One-off timer:** fires once, either after a countdown or at a specific clock time.
   - Quick chips: 15 min, 30 min, 1 h, 2 h, plus a custom option.
   - Once it fires and is acknowledged, it moves to a "Recent timers" section, where it can be restarted with one tap. Older timers are auto-cleared after 7 days.
   - Snooze works as normal.

## 2. Start time (fixed, irregular and stepped)

- **Start now:** the first alert fires one interval after saving. For irregular alarms, the first window starts now.
- **Start at a specific time:** today, or tomorrow if that time has passed.
- Interval entry uses a **custom hours/minutes duration picker** (steppers or wheels) plus a numeric keyboard option. Do **not** use the Material 3 `TimePicker` for durations, because it picks a clock time and shows AM/PM.

## 3. Dashboard

- A hero card for the next alert across all alarms.
- The alarm list shows, for each alarm: label, a type icon, a summary (e.g. "Every 30 min", "4× per hour, irregular", "Stepped · 2 of 3", "Timer · 12:40"), the next trigger (exact time and relative time, except irregular alarms, which show "within window"), time remaining if a run-for limit is set, and an on/off switch.
- Swipe to delete with Undo. Duplicate an alarm from the overflow menu.
- A calm empty state with one button to create an alarm.

## 4. Quick presets

- A horizontal chip row: **15 min, 30 min, 1 h, 2 h, 4 h** (fixed interval), plus user-saved presets of any type.
- Tapping a chip opens the editor pre-filled so the user can confirm or adjust it.
- Custom presets can be renamed and deleted. Built-in presets can be hidden but not deleted.

## 5. Session controls and auto-stop

- **Pause all / Resume all:** pausing cancels all pending triggers and remembers which alarms were enabled. Resuming recalculates from the current time, with no catch-up bursts. Stepped schedules resume at the step they were on.
- **Run for (optional):** stored as an absolute `endsAt` timestamp. Never schedule a trigger past it. When it is reached, disable the alarm automatically and post a quiet "Finished" notification.

## 6. Alert modes (per alarm)

1. **Sound + vibration**, with a ringtone picker (`RingtoneManager.ACTION_RINGTONE_PICKER`)
2. **Sound only**
3. **Vibrate only**, with a distinctive multi-pulse `VibrationEffect` waveform
4. **Discreet escalation (pocket mode):** two gentle pulses → after 30 s with no acknowledgement, a stronger repeating vibration → after 60 s, a chime with volume ramping up
5. **Silent banner only:** no sound or vibration

Implementation rules:

- Play sound with `AudioAttributes.USAGE_ALARM`. Use `VibratorManager` on API 31+.
- **Ring timeout** (default 3 min, configurable): stop ringing, log the alert as **Missed**, keep the schedule running, and leave a "Missed: [label] at [time]" notification.
- If a paired watch is connected, a per-alarm option **"Watch only"** silences the phone and alerts only on the watch. See the Wear OS section.

## 7. Alert screen

- When an alarm fires, post a high-priority notification on a dedicated alarm channel with a **full-screen intent** that launches `AlertActivity`.
- `AlertActivity` uses `setShowWhenLocked(true)` and `setTurnScreenOn(true)`, is excluded from recents, and uses `launchMode="singleInstance"`. Do **not** use `SYSTEM_ALERT_WINDOW`.
- On Android 14+, check `canUseFullScreenIntent()`. If it returns false, fall back to a heads-up notification with actions, and show a card linking to `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
- The screen has two large buttons (at least 72 dp tall and full-width):
  - **Done:** keeps the original cadence (or moves to the next step or the next random time).
  - **Snooze 5 min:** doesn't move the underlying schedule.
- Mirror both as notification actions. Volume and power keys count as Snooze.
- If several alerts are due at once, queue them and show them one at a time.

## 8. Scheduling engine (the most important part, so get this right)

- **No service running while idle.** Schedule only the single next trigger per alarm.
- Use **`AlarmManager.setAlarmClock()`** for every trigger. Do not use `setExactAndAllowWhileIdle()`, which Doze throttles to roughly one trigger per 9 minutes.
- **Exact alarm permission:** declare `USE_EXACT_ALARM` (API 33+) and `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"`. Check `canScheduleExactAlarms()` and handle `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
- **While ringing only:** start a short-lived foreground service (type `mediaPlayback`) from the receiver for sound, vibration and escalation. Stop it on Done, Snooze or timeout.
- **Pure `ScheduleCalculator` in `:core`** with no Android dependencies, used by both phone and watch:
  - Fixed: the next trigger is the first `anchorAt + n × intervalMs` after now. Never "now + interval". Missed slots are skipped, never fired in a burst.
  - Irregular: generate the random times for the current window lazily and persist them (hidden from the UI), so they stay stable across process death and reboot. When a window passes, generate the next one.
  - Stepped: track `stepIndex` and `repeatIndex`. Each step anchors to the planned end of the previous step, so the schedule doesn't drift. Skipped slots still advance the counters.
  - One-off: a single `fireAt`.
  - Snooze: stored as `snoozeUntil`. If a snooze would reach or pass the next regular trigger, drop the snooze.
  - Inject the random source and the clock, so tests are deterministic.
- Each alarm gets a unique, stable `PendingIntent` request code (from its Room id). Use `FLAG_IMMUTABLE`.
- **Rescheduling receiver:** listens for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` and the exact-alarm permission change.

## 9. Permissions and onboarding

- Three short, elegant onboarding cards, plus a **Settings → Permissions health** screen with ✅ or ⚠️ status cards. Each card opens the right system screen.
  - `POST_NOTIFICATIONS` (API 33+)
  - Exact alarms (only if not granted)
  - Full-screen intent (API 34+)
  - Optional battery card: opens `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, with a note about Samsung, Xiaomi, OnePlus and Huawei "Unrestricted" settings. Do **not** call `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- If a permission is revoked, show a persistent dashboard banner.

## 10. Polish and accessibility

- TalkBack support, large font support, touch targets of at least 48 dp, and correct behaviour on rotation and after process death.
- A **Test alert** button in the editor.
- Settings: default alert mode, snooze length, ring timeout, 12/24-hour time (defaults to the system setting), theme (system / light / dark / true-dark), dynamic colour toggle, Export/Import.

# Wear OS companion (`:wear`)

- Built with Compose for Wear OS, Horologist where helpful, Material 3 for Wear OS styling, and the same calm design language: a dark background, one accent colour and large numerals.
- **Sync:** the phone is the source of truth. It pushes alarm definitions and the upcoming schedule to the watch via `DataClient`. Alert events and responses go via `MessageClient`. This is Bluetooth only, with no internet. Declare a capability (e.g. `interval_alarm_watch`) so the phone knows the watch app is installed and reachable.
- **When an alarm fires and the watch is connected:** the phone sends a message, and the watch shows a full-screen alert activity with strong, distinctive haptics plus large **Done** and **Snooze** buttons. Responses go back to the phone.
- Mark the phone's alert notification `setLocalOnly(true)` when the watch app is handling it, so the watch doesn't get a duplicate bridged notification.
- **When the phone is out of range** (e.g. left in a locker): the watch keeps a synced copy of the schedule and runs `ScheduleCalculator` itself. It schedules its own alarms with `setAlarmClock()` and alerts on its own.
- **Deduplication:** every occurrence has an id (`alarmId + scheduledAt`). Whichever device is acknowledged first wins. The other cancels its pending alert or ringing for that occurrence. Responses queued while disconnected sync when the devices reconnect, and the phone reconciles them.
- **Watch UI:** a list of alarms with on/off toggles and a one-tap **Pause all**. Creating or editing alarms is phone-only, to keep the watch simple.
- **Tile:** shows the next alert (label plus countdown), with a Pause all button.
- **Complication:** shows the time to the next alert (short text).
- Watch haptic patterns: a distinct waveform per alert mode, with escalation if not acknowledged.

# Data model (minimum)

- `AlarmEntity`: id, label, type (FIXED / IRREGULAR / STEPPED / ONE_OFF), intervalMs?, windowMs?, alertsPerWindow?, minGapMs?, currentWindowStart?, pendingRandomTimes (JSON, hidden)?, steps (JSON list of `{intervalMs, repeats}`)?, afterLastStep (STOP / CONTINUE)?, stepIndex, repeatIndex, fireAt?, startMode, anchorAt, endsAt?, enabled, pausedByMaster, alertMode, watchOnly, ringtoneUri?, snoozeUntil?, createdAt
- `PresetEntity`: id, name, type, config (JSON), builtIn, hidden
- `AlertLogEntity`: id, alarmId, occurrenceId, scheduledAt, outcome (DONE / SNOOZED / MISSED), respondedAt?, respondedOn (PHONE / WATCH). Keep 30 days.

# How to work (follow this order, and stop after each phase)

1. **Phase 1:** Gradle setup and modules (`:app`, `:wear`, `:core`), both manifests (permissions, receivers, activities, foreground service type, Wear capability), notification channels, Hilt, and the theme and design tokens (colour, type and shape).
2. **Phase 2:** Room entities, DAOs, migrations strategy, repositories, DataStore settings, JSON Export/Import.
3. **Phase 3:** `ScheduleCalculator` in `:core` with **unit tests** covering fixed-interval drift, missed slots, snooze collision, auto-stop, the start time already passed, DST/time-zone changes, irregular windows (minimum gap, all alerts inside the window, impossible-configuration validation, stable after restart), stepped progression (step transitions, stop vs continue, skipped slots), and one-off timers. Then `AlarmScheduler`, receivers and the ringing service.
4. **Phase 4:** Phone Compose UI: dashboard, editor for all four types, presets, recent timers, alert screen, settings, permissions health, onboarding, plus previews.
5. **Phase 5:** Wear OS app: Data Layer sync, watch alert screen and haptics, standalone fallback scheduling, deduplication, tile and complication.
6. **Phase 6:** Review pass for lint warnings, Android 14/15 behaviour changes, a confirmation that no `INTERNET` permission appears in any merged manifest, and Play policy checks. Write a `README.md` listing the Play Console declarations I'll need to make (exact alarm, full-screen intent, foreground service type, Data Safety).

After each phase: make sure the project **builds with no errors**, list the files you created or changed, note any assumptions, then wait for me to say "continue". Write complete files. No placeholders, `TODO`s or "rest of code here".
