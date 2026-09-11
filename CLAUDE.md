# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A single-`app`-module Android take-home: detect ENTER/EXIT of hardcoded venue geofences via Play Services, and while inside a venue, scan for that venue's iBeacon and report proximity. One Compose screen, Kotlin, Hilt DI, coroutines/Flow throughout. Full design rationale and a decision-by-decision history live in `planning.md` and `DECISIONS.md` at the repo root — consult those before re-deriving an architectural choice; don't duplicate their content here.

## Commands

All commands are run from the repo root; use `.\gradlew.bat` on Windows or `./gradlew` on macOS/Linux.

```
gradlew :app:compileDebugKotlin                          # compile main sources
gradlew :app:compileDebugUnitTestKotlin                   # compile test sources
gradlew :app:testDebugUnitTest                             # run all unit tests
gradlew :app:testDebugUnitTest --tests "*VenueMonitorTest"  # run one test class
gradlew :app:testDebugUnitTest --tests "*VenueMonitorTest.geofence ENTER starts scanning*"  # one test method
gradlew :app:assembleDebug                                 # build debug APK
gradlew :app:assembleRelease                               # build release APK (R8 currently off, see DECISIONS.md)
gradlew :app:lintDebug                                     # standard Android Lint (no ktlint/detekt configured)
```

There is no `androidTest`/instrumented suite worth running here — everything testable is plain JVM unit tests under `app/src/test/`; the BLE/geofence integration path can only be verified on physical hardware (see the take-home prompt's own setup-constraints section).

After any non-trivial change, run the compile + test sequence above (not just `assembleDebug`) — this project has repeatedly hit build-tool version-compatibility issues (see "Known gotchas" below) that only surface at compile time.

## Architecture

**Package-by-feature, not by layer-purity dogma:**
- `data/{ble,geofence,location,permission,service,venue}/` — one interface + one real Android-backed implementation per concern (`BleScanner`/`PlatformBleScanner`, `GeofenceSource`/`PlayServicesGeofenceSource`, `LocationSource`/`FusedLocationSource`, `PermissionChecker`/`AndroidPermissionChecker`, `ServiceStarter`/`AndroidServiceStarter`). Every one of these has a `Fake*` counterpart in `app/src/test/.../fakes/`. This is the load-bearing pattern in the codebase: anything that touches the Android framework goes behind an interface here so `domain/` stays plain-JUnit-testable. Follow it for anything new that touches Context, a system service, or a permission check.
- `domain/` — pure Kotlin, no Android imports except where truly unavoidable. Holds the actual business logic: `VenueState`/`VenueEvent`/`VenueStateMachine` (the pure reducer), `VenueMonitor` (see below), `RssiSmoother`, `BeaconLostTimer`, `GeofenceExitPoller`, `ContainmentChecker`, `DistanceEstimator`, `ProximityClassifier`.
- `service/` — the two real Android components, `GeofenceBroadcastReceiver` and `VenueMonitorService`. Deliberately thin (see below).
- `ui/` — one Compose screen (`VenueScreen`) and a thin `VenueViewModel` that just forwards `VenueMonitor`'s `StateFlow`s to Compose.
- `di/` — `VenueCheckInApplication` (`@HiltAndroidApp`) and `DataModule` (all the `@Binds` for the above interfaces).

**The central design decision to understand before touching any monitoring/scanning code:** `VenueMonitor` (`domain/VenueMonitor.kt`) is an app-process-lifetime Hilt `@Singleton`, not the ViewModel and not the Service, and it owns *everything* — geofence-transition handling, BLE scan start/stop, RSSI smoothing, beacon-lost timing, geofence-exit polling, both in-memory logs. This was a deliberate refactor (see DECISIONS.md "Scanning ownership moved to VenueMonitor"), for two reasons: it needs to survive independent of any Activity/ViewModel (the take-home's own premise is that a transition can fire with no UI alive), and it needs to stay unit-testable without Robolectric (an Android `Service` can't be exercised in a plain JVM test). `VenueMonitorService` correspondingly contains zero business logic — its only job is holding the foreground-service protection that keeps the process alive while Inside a venue (`startForeground()` in `onCreate()`, nothing else). `VenueViewModel` is a pass-through only.

**State flow:** `VenueStateMachine.reduce(current: VenueState, event: VenueEvent): VenueState` is a pure reducer (`Outside` / `Inside(venue)` / `InRange(venue, proximity, rssi)`), idempotent on a duplicate `Enter`/`Exit` for the venue already tracked — a no-op returns the *same* object reference (checked via `===`, not `==`, in `VenueMonitor.applyStateChange`), not just an equal one. `VenueMonitor` derives BLE scan start/stop from the state transition, not from the raw event.

**Two independent sources can each report "Entered X" for the same venue**, and both are intentional: `VenueMonitor.registerGeofences()`'s fast local check (`LocationSource` + `ContainmentChecker`, near-instant) and Play Services' own opportunistic `INITIAL_TRIGGER_ENTER`/real-crossing detection (`GeofenceBroadcastReceiver` → `PlayServicesGeofenceSource` → the same `VenueMonitor`). `onGeofenceTransition`'s `insideVenueIds` set dedupes them before either logs or drives the state machine.

**Known gotchas, don't re-discover these:**
- Real Play Services geofence transitions — especially EXIT — can take *minutes* to fire on real hardware. This is expected OS latency, not a bug; don't chase it as one.
- `GeofenceBroadcastReceiver`'s `startForegroundService`/`stopService` calls must stay synchronous inside `onReceive()`. The Android 12+ background-start exemption for system-triggered broadcasts only covers that receiver's own call stack — moving the call into an async callback (even one scheduled milliseconds later) can throw `ForegroundServiceStartNotAllowedException` when the app is genuinely backgrounded, which is exactly the scenario this exists for.
- `VenueState` tracks only one venue at a time — overlapping geofences aren't handled correctly yet (see the doc comment on `VenueState.venueOrNull()` for the specifics of what breaks).
- This project relies on AGP 9's built-in-Kotlin support being *disabled* (`android.builtInKotlin=false` / `android.newDsl=false` in `gradle.properties`, classic `org.jetbrains.kotlin.android` plugin applied explicitly, `jvmTarget` pinned to 11 in `app/build.gradle.kts`) because KSP/Hilt don't support AGP's built-in-Kotlin mode yet. Don't remove these to "simplify" the build.
- Beacon identity is iBeacon (UUID + major + minor), matched via a manufacturer-data `ScanFilter` in `PlatformBleScanner` *plus* a software-side identity check — the radio-level filter alone was briefly suspected of being unreliable during debugging but turned out fine once an unrelated permission bug was fixed; don't remove the software-side check regardless, since one UUID can carry multiple venues' major/minor.
