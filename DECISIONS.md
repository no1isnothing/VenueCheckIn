# Decisions

Living log, filled in as calls get made — not a final writeup yet. Structured around what the prompt asks DECISIONS.md to cover; sections marked **Pending** are tracked in `planning.md` and will be settled (and moved up here) before submission.

## Dependency injection: Hilt

Originally planned hand-rolled DI (fewer moving parts, lower setup risk in a timeboxed exercise). Reversed after deciding to build this as something meant to be extended rather than a one-shot submission: Hilt's KSP setup cost is cheap to pay now, while it's still just a dependency-wiring step, and gets expensive to retrofit once more classes exist. It also makes swapping fakes for production bindings in tests mechanical (`@Module` + `@TestInstallIn`) rather than manual constructor-threading, which matters more as the class count grows.

**Build-tooling side effect:** wiring up Hilt surfaced that this project was relying on AGP 9's new "built-in Kotlin" compilation mode, which KSP (and therefore Hilt) doesn't yet support — [open upstream issue](https://github.com/google/ksp/issues/2729), unresolved as of Dec 2025. Fixed by opting back into the classic `org.jetbrains.kotlin.android` Gradle plugin (`android.builtInKotlin=false` / `android.newDsl=false` in `gradle.properties`), which then needed an explicit `jvmTarget` set to match `compileOptions` (11). Google's docs say this opt-out is removed in AGP 10 — worth rechecking KSP/AGP compatibility before any future AGP upgrade past 9.

## Logging: Timber

Added over raw `Log`/`println`. Geofence and BLE transitions are timing-sensitive and, per the prompt's own setup-constraint warning, often only reproducible on real hardware with the app fully backgrounded and no debugger attached — `Timber.d(...)` piped to `adb logcat` is the practical way to see what the state machine did when there was no UI alive to observe it directly.

## Where test fakes live: `src/test/java/.../fakes/`

Considered three placements: alongside the interface/impl in the main source set, in a dedicated `src/test/.../fakes/` package, or in an AGP `testFixtures` source set (the mechanism meant for fakes shared between `test` and `androidTest`). Picked `src/test/.../fakes/` — every unit specified in the "Testability" section (containment, RSSI smoothing, beacon-lost timeout, the state machine including EXIT-stops-scanning) is pure Kotlin exercised by JVM unit tests; nothing calls for an instrumented test, and the prompt's own simulation section confirms the real BLE/geofence integration path can only be verified on physical hardware anyway, not via `androidTest`. `testFixtures` is the correct move if that ever changes, but not worth the extra source-set setup pre-emptively here.

## Release optimization (R8) and `testFixtures`: investigated, not changed

Both tested empirically (temporarily flipped on, measured/observed, reverted — nothing shipped). Full detail in `planning.md` §15/§16.

- **R8 (`optimization.enable`):** works, but on this project's AGP version (9.2.1) needs an extra `android.r8.gradual.support=true` flag the simplified DSL doesn't need starting AGP 9.3. With it on: unoptimized release APK 23.7 MB vs. optimized 1.69 MB, no R8 warnings against the current dependency set. Left off for now — minified builds are harder to debug mid-development (de-obfuscation required, silent runtime failures instead of compile errors for anything under-covered by keep rules); flip on later, right before the build that actually ships. Also surfaced an unrelated pre-existing issue, since fixed during scaffolding: `compileSdk` was bumped `36.1 → 37` (`androidx.core:core-ktx:1.19.0` and `androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0` require 37) — `checkDebugAarMetadata`/release builds/`androidTest` compiles are unblocked now.
- **`testFixtures`:** works on an application module (not library-only, despite some docs suggesting otherwise), but Kotlin sources in it need an experimental flag (`android.experimental.enableTestFixturesKotlinSupport=true`, silently skips `.kt` files without it) and this project's project-wide Compose compiler plugin then fails on the testFixtures compile task unless Compose deps are separately added to `testFixturesImplementation` — friction with no present payoff, since §15 above already established nothing here needs `androidTest`-shared fakes. Confirms the `src/test/.../fakes/` decision rather than changing it.

## "Already inside" (requirement 2) — Pending

Leaning toward combining a manual containment check (fetch current location right after registering geofences, compute distance vs. each venue) with `INITIAL_TRIGGER_ENTER` left registered as a backstop, with the state reducer treating a duplicate ENTER for a venue already `Inside`/`InRange` as a no-op. Rationale and alternatives considered are in `planning.md` §4/§7. Not locked in yet — still reviewing.

## RSSI smoothing & beacon-lost timeout — Pending

Leaning toward a moving average (window of 5) over EMA or median, and a 10s beacon-lost timeout (sized off typical simulator/iBeacon advertising intervals of ~100ms–1s, with margin for a couple of dropped packets). Reasoning and alternatives in `planning.md` §6. Not locked in yet.

## Foreground service decision: foreground-only-while-Inside

Decided: `BroadcastReceiver` calls `startForegroundService()` on confirmed ENTER, `VenueMonitorService.stopSelf()` on confirmed EXIT — the service's lifetime is tied to the same Inside/Outside gate as the scanner itself, rather than an always-on foreground service or a plain background service. A plain background service was ruled out specifically because requirement 9's scenario (geofence transition firing with no UI alive) is exactly the condition Android's background execution limits and BLE scan throttling target — it risks silently going dark partway through an Inside session. Full option comparison and the background-service reasoning are in `planning.md` §8/§14.

Implementation notes carried over from planning: `startForeground()` must be called immediately in the service (no async setup first, see the §5 PendingIntent/background-start gotcha), and the manifest needs `FOREGROUND_SERVICE_CONNECTED_DEVICE` declared with `foregroundServiceType="connectedDevice"`.

## Module structure: stayed single-module

Considered splitting into a Gradle module per package (`:domain`, `:data-venue`, `:data-geofence`, `:data-ble`, `:data-location`, `:service`, `:ui`, plus `:app`) to better signal "built to extend." Estimated 3–5 hours for the full per-package split vs. 1.5–2.5 hours for a coarser 3-module version (`:domain`, `:data`, `:app`) — most of that cost is Gradle/Hilt friction, not moving files: this project already hit an AGP 9 "built-in Kotlin" vs. KSP incompatibility once (see Dependency injection above), and each additional Android library module carrying `@Inject`/`@Binds` would need the same fix repeated or centralized via a convention plugin; plus a real `api`/`implementation` visibility pass per dependency edge, and moving each fake into its owning module's own `test` source set instead of `:app`'s.

Decided against it, and kept the current single-module structure. Reasoning: the prompt is explicit that this is "one screen," 6–10 hours total, and structure is graded partly on *not* over-building — 8 Gradle modules for a single-screen app with no feature boundaries to enforce (there's nothing for a `:ui` or `:service` module to protect against) reads more like over-engineering than foresight to a reviewer, especially layered on top of a codebase that's still mostly `TODO()` stubs. A `:di` module also doesn't actually work as conceived — Hilt's `@Binds` graph needs to see every impl, so that wiring collapses back into `:app` regardless of how many modules exist below it. Full reasoning in the conversation; not written up in `planning.md` since nothing was implemented.

## Battery tradeoffs considered

- Scan filtering at the radio level (`ScanFilter` matching manufacturer data + UUID mask) rather than scanning unfiltered and discarding in the callback — cheaper than CPU-side filtering.
- Foreground-service-only-while-Inside instead of always-on, so the radio/scanner overhead is bounded to the time actually spent inside a venue.
- Activity Recognition as an optional stretch goal to duty-cycle scan aggressiveness (`LOW_LATENCY` while walking, `BALANCED`/`LOW_POWER` while still) — explicitly not required, only attempted if time remains; see `planning.md` §9.

## What I'd do with another week

To be filled in once the core implementation is further along — premature to speculate before the pending decisions above are settled and something is actually built against them.
