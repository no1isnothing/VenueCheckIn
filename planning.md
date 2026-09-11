# Venue Check-In — Planning

Scratch planning doc for the take-home. Not a spec — a starting point to code against and revise as reality (real hardware, real GPS jitter) pushes back.

## 1. Restated scope

One venue-monitoring flow: `Outside → Inside(venue) → InRange(venue, proximity, rssi) → Inside → Outside`, driven by:
- Play Services geofencing (ENTER/EXIT only, no DWELL)
- Platform `BluetoothLeScanner`, gated so it only ever runs while Inside a venue
- A single `StateFlow<VenueState>` + timestamped log, shown on one Compose screen

Out of scope, explicitly: dwell, persistence beyond memory, multi-beacon positioning, backend, design polish, CI.

## 2. Proposed module shape (single `app` module, package-by-feature)

```
data/
  venue/         Venue, BeaconIdentity, hardcoded/JSON venue list
  geofence/       GeofenceSource (interface) + Play Services impl + fake
  ble/            BleScanner (interface) + platform impl (manual iBeacon parsing) + fake
  location/       LocationSource (interface) — wraps "am I already inside" check
domain/
  VenueState (sealed interface), RssiSmoother, BeaconLostTimer, VenueStateMachine (pure reducer)
service/
  VenueMonitorService (foreground service, owns scanner lifecycle)
  GeofenceBroadcastReceiver
ui/
  MainActivity, VenueScreen, VenueViewModel
```

Rationale for interfaces at the `data/` boundary: `GeofencingClient` and `BluetoothLeScanner` are both hard to instantiate/fake directly (final classes, system services), so requirement (a)/(b)/(c) testability depends on wrapping them now, not bolting it on later.

## 3. Venue & beacon identity — decision

**Decided: iBeacon (UUID + major + minor).** (Briefly switched to Eddystone-UID and back — see history note below.) Reasons:
- The take-home spec names it directly as the first example format ("an iBeacon UUID + major + minor, or a service UUID, or a local name"), which settles it over a functionally-comparable alternative.
- Every simulation option listed in the prompt (Beacon Simulator, Beacon Toy, nRF Connect advertiser) supports iBeacon out of the box; Eddystone/service-UUID support is spottier across those apps.
- The manual-parse requirement is well-documented and bounded: Apple manufacturer ID `0x004C`, iBeacon sub-type `0x02`, length `0x15` (21), then 16-byte UUID, 2-byte major, 2-byte minor, 1-byte signed calibrated Tx power — all inside `ScanRecord.getManufacturerSpecificData(0x004C)`.
- One venue = one UUID, distinguished by major (or minor) per venue, so the "venue's beacon" mapping is just a lookup table, no dynamic pairing needed.

Document in README exactly which simulator app + UUID/major/minor per venue.

*History:* briefly reconsidered Eddystone-UID given Beacon Simulator broadcasts both cleanly — Eddystone's `ScanFilter.setServiceUuid(0xFEAA)` is a marginally cleaner filter than iBeacon's manufacturer-data-only option, at the cost of one extra edge case (UID/URL/TLM sibling frames sharing the same service UUID, needing a frame-type byte in the filter mask to exclude). Full comparison is in the conversation. Reverted to iBeacon since the spec names it explicitly — not worth diverging from the example format for a marginal filtering-cleanliness edge.

## 4. Geofencing

**Registration:** one `GeofencingClient.addGeofences()` call for all 2–3 venues at app start (once permissions are granted), `GEOFENCE_TRANSITION_ENTER | GEOFENCE_TRANSITION_EXIT` only, `GeofencingRequest.INITIAL_TRIGGER_ENTER`.

**Gotcha — PendingIntent flags:** Android 12+ requires the geofence `PendingIntent` to be explicitly `FLAG_MUTABLE` (combined with `FLAG_UPDATE_CURRENT`). This is the opposite of the usual "always prefer FLAG_IMMUTABLE" lint guidance — if you use `FLAG_IMMUTABLE` here, `GeofencingEvent.fromIntent()` can't read the transition data and nothing fires. Worth a comment in code pointing at the doc, since it looks like a mistake to a future reader (and to lint).

**Requirement 2 — "already inside": three options**

| Option | How | Tradeoff |
|---|---|---|
| A. `INITIAL_TRIGGER_ENTER` only | Rely on Play Services to synthesize an ENTER if already inside at registration time | Simplest, one code path (everything flows through the same BroadcastReceiver). But it's opportunistic — evaluation can be delayed by seconds to minutes depending on device, and isn't guaranteed to fire promptly, which conflicts with "the app must still **end up** in the Inside state," not "eventually might." |
| B. Manual check via `getCurrentLocation()`/`lastLocation` | Immediately after registering geofences, fetch current location once and compute distance-to-venue myself (`Location.distanceBetween`), set state directly | Fast, deterministic, testable (it's just a pure containment function). Adds a second code path that has to agree with the geofence-driven one (see §7 dedupe). |
| C. Both, combined | Do B for immediate correctness, keep A registered so real-world transitions still work | Most robust — the two paths are redundant on purpose. Requires the state machine to treat a duplicate ENTER for a venue you're already Inside for as a no-op (idempotent), so B firing first doesn't get clobbered or double-counted when A's synthetic ENTER arrives later. |

**Recommend C.** It's the one the prompt calls out as "the requirement we care about most," and redundancy paired with an idempotent reducer is cheap here. This is exactly the kind of design call worth narrating on the call — build the reducer to accept "ENTER while already Inside(same venue)" as a safe no-op from day one rather than patching it in when the duplicate shows up in testing.

## 5. BroadcastReceiver → service wiring

Deliver via a manifest-registered `BroadcastReceiver` (`GeofenceBroadcastReceiver`), not a dynamically-registered one — the process may not be alive when the transition fires, so it must be woken via manifest registration.

**Gotcha — starting a foreground service from the receiver:** Android 12+ blocks starting a foreground service "from the background" in general, but a `PendingIntent` broadcast triggered by a system component (Play Services delivering a geofence transition) is one of the documented exemptions. So `context.startForegroundService(...)` from inside `onReceive()` is allowed — but only if `startForeground()` is called quickly inside the service's `onStartCommand`/`onCreate`; don't do async setup (e.g., await a coroutine) before calling it, or you risk `ForegroundServiceDidNotStartInTimeException`.

## 6. BLE scanning & ranging

**Scan filtering:** use `ScanFilter` with manufacturer-data + mask matching on the Apple company ID and the venue's iBeacon UUID bytes (mask out major/minor), rather than an unfiltered scan + filter-in-callback. This is both a battery win (radio-level filtering vs. CPU-side) and reduces log noise from unrelated beacons in the room. Still parse the full manufacturer payload manually in the callback (requirement explicitly wants manual parsing, and you need major/minor/RSSI anyway).

**Smoothing:** simple moving average vs. EMA vs. median.
- Median is most robust to single-packet outliers but needs a buffer and is a bit more code.
- Moving average (window ~5 samples) is simple, testable, and good enough for a 4-bucket proximity output.
- EMA (e.g. α=0.3) needs no buffer, reacts a bit faster to real movement.

**Decided: moving average, window of 5.** Easiest to explain and test with fixture data, and the bucket boundaries (Immediate/Near/Far) already have more resolution than the smoothing precision needs to justify EMA's tuning knob.

**Beacon-lost timeout (N seconds):** pick N based on the simulator's advertising interval, not a guess. Beacon Simulator/Beacon Toy typically advertise every ~100ms–1s; a real iBeacon is similar. Recommend **N = 10s** — generously more than 10x a typical advertising interval, so it comfortably survives a couple of dropped packets or brief scan-throttling from Doze/App Standby, but still reads as "gone" well before it'd be confusing in the UI log. Document this reasoning in DECISIONS.md and make N a named constant so it's trivially tunable after watching real hardware behavior. (Still just a lean, not explicitly confirmed — flag if a different N is preferred.)

Implementation shape: each accepted scan result resets a coroutine `Job` (`delay(N.seconds)` → emit `Unknown`/absent); testable by injecting a `TestDispatcher`/virtual clock rather than real `delay`.

## 7. Smoother ENTER/EXIT transitions — options (and one to avoid)

Two distinct problems hide under "smoother transitions":

**(a) Duplicate/racing ENTER events.** With §4 option C, both the manual containment check and the real geofence callback can independently report ENTER for the same venue. Fix: make the reducer idempotent on "ENTER while already Inside/InRange for that venue" — a no-op, not a scanner restart. Same for duplicate EXITs.

**(b) GPS jitter causing rapid EXIT→ENTER flapping at the boundary.** Options:
- **Radius padding** — register the geofence with a slightly larger radius than the venue's true boundary (e.g. +15–20m) to build in margin for location noise. Simple, no extra state, costs nothing at runtime.
- **Two-tier geofence (inner/outer)** — register both the stated radius and a larger buffer radius, only transition to Outside when the *outer* fence exits. Handles jitter better but adds a second geofence per venue and extra state to reason about — likely more complexity than this exercise needs; worth mentioning as a "with more time" idea rather than building it.
- **Software debounce on the stop side** (delay tearing down the scanner/service a few seconds after EXIT, cancel if ENTER re-fires) — **do not do this.** Requirement 4 is explicit and literal: "the app must never be scanning while the user is outside every venue." A stop-side debounce means scanning *while outside*, which directly violates a requirement the prompt calls "the heart of the exercise." This is worth flagging precisely because it's the intuitive-looking fix for flapping and is the wrong one here — note it and the reason in DECISIONS.md rather than implementing it.

**Recommend:** idempotent reducer (a) + radius padding (b). Keep EXIT → stop-scanning synchronous and immediate, no delay, per requirement 4.

## 8. Service vs. foreground service — options

| Option | Shape | Tradeoff |
|---|---|---|
| 1. No service | Do everything in the `BroadcastReceiver` + a process-wide coroutine scope | Doesn't survive the receiver returning / app backgrounded; Doze and background-execution limits mean this is unreliable for anything that must outlive the broadcast. Not viable given requirement 9's framing. |
| 2. Always-on foreground service | Start on app launch, own geofence registration + scanner + state machine for the whole app lifetime, regardless of Inside/Outside | One consistent owner, no cold-start latency on ENTER, simplest lifecycle reasoning. Cost: a persistent notification even while Outside, and a service alive 100% of the time even though it's only doing meaningful work while Inside. |
| 3. Foreground service only while Inside | `BroadcastReceiver` calls `startForegroundService()` on confirmed ENTER, service calls `stopSelf()` on confirmed EXIT; scanner lives entirely inside the service | Notification only appears while actually monitoring proximity — better matches "gate BLE scanning on geofence presence" extended to the service itself, and is easy to justify to a reviewer ("we only hold a foreground service when we're doing the foreground-service-shaped thing"). Cost: must call `startForeground()` immediately in the service (see §5 gotcha), and needs `FOREGROUND_SERVICE_CONNECTED_DEVICE` (Android 14+ type for BLE) declared. |

**Recommend option 3**, and say so explicitly in DECISIONS.md — it's a defensible call either way per the prompt, but tying the foreground service's *lifetime* to the same Inside/Outside gate as the scanner itself makes the "why a foreground service, why now" story clean in one sentence.

Where does the `StateFlow` live so both the service and a possibly-dead UI agree on it? Recommend hosting it in the service (or an `Application`-scoped singleton the service publishes to), with the UI `ViewModel` binding to the service when the Activity is visible, and falling back to "last known state" when unbound. Geofence-driven "already inside" (§4 option C) should message the service directly (start it, or send it a command) rather than going through a separate path — one writer to the state.

## 9. Activity Recognition — optional, not core

Not in the graded requirements, so treat as a stretch goal to attempt only if time remains near the end of the 6–10 hour box — and structure it so the core Outside/Inside/InRange flow works with zero dependency on it.

Options for what it'd actually do:
- **Duty-cycle scan mode while Inside**: `SCAN_MODE_LOW_LATENCY` while `WALKING` (likely closing/opening distance, want responsive updates), drop to `SCAN_MODE_BALANCED`/`LOW_POWER` while `STILL` (proximity unlikely to be changing, save battery). This is the one worth trying if there's time — it's a direct, explainable battery optimization.
- Widening the smoothing window while `STILL`: marginal value, extra state, probably not worth the complexity for a one-screen exercise.
- Gating scanning on not-`IN_VEHICLE`: rare edge case (being in a vehicle inside a venue's geofence), low value.

Cost to weigh: it's a 4th runtime permission (`ACTIVITY_RECOGNITION`, Android 10+) with its own denial-handling story, which adds permission-flow surface area the graders didn't ask for. If implemented, it must degrade to the non-optimized scan behavior silently when denied — never block core functionality on it.

## 10. Testability plan

- `GeofenceSource` interface: `registerVenues(venues)`, exposes transitions; fake emits scripted `Enter`/`Exit` events without touching `GeofencingClient`.
- `LocationSource` interface: `currentLocation(): Location?`, used for the "already inside" check; fake returns fixture coordinates — lets containment math be tested without a device.
- `BleScanner` interface: emits a `Flow` of an app-owned `BeaconSighting` DTO (not framework `ScanResult`, which is awkward to construct in tests) — the platform impl does the manual iBeacon parsing and maps into `BeaconSighting` at the boundary; the fake just emits `BeaconSighting` values directly on a schedule.
- Pure, directly-testable units: containment check (`Location.distanceBetween` wrapped in a function), `RssiSmoother` (window/EMA, tested with fixture RSSI sequences), beacon-lost timer (inject a `TestDispatcher`/virtual time), and the state-machine reducer as `(VenueState, Event) -> VenueState` — table-driven tests, including the specific case requirement (c) calls out: EXIT while `InRange` must produce `Outside` *and* a stop-scan side effect, not just a state change.

## 11. Permissions plan

Staged, in this order:
1. `ACCESS_FINE_LOCATION` (+ implied `ACCESS_COARSE_LOCATION`) — needed before registering any geofence.
2. `BLUETOOTH_SCAN` — declare with `android:usesPermissionFlags="neverForLocation"` in the manifest, since proximity/RSSI bucketing never derives physical position from BLE (this is true here — Android will otherwise silently drop scan results if this flag is absent and location permission/services are off).
3. `ACCESS_BACKGROUND_LOCATION` — separate, later request, only after foreground location is granted (OS enforces this ordering on 11+; bundling it with #1 will get it silently ignored or auto-denied by the system dialog).
4. `POST_NOTIFICATIONS` (Android 13+) — needed for the foreground service's required notification.
5. (stretch) `ACTIVITY_RECOGNITION`, only if §9 gets built.

Each denial needs to be individually representable in the UI (a "what's missing and why" line per permission), not a single generic "permissions needed" blob — e.g. background location denied should read differently from BLE scan denied, since they break different parts of the flow (geofencing vs. ranging).

**Correction, verified on real hardware during BLE testing:** point 2's assumption — that `neverForLocation` removes the need for `ACCESS_FINE_LOCATION`/Location Services to get BLE scan results — did not hold on the actual test device. Even with `BLUETOOTH_SCAN` declared with `neverForLocation` and granted, scan results were silently empty until `ACCESS_FINE_LOCATION` was also granted. This matches real-world reports that the `neverForLocation` exemption is inconsistently honored across OEM Bluetooth stacks (manifest-merger conflicts from a dependency re-declaring `BLUETOOTH_SCAN` without the flag is one documented cause; OEM-specific enforcement is another) — not something to rely on unconditionally. **Practical fix:** request both `BLUETOOTH_SCAN` and `ACCESS_FINE_LOCATION` together on API 31+ (not either/or), and gate scanning on both being granted. See `VenueScreen.kt`'s permission check.

## 12. Dependencies added now

Added to `libs.versions.toml` / `app/build.gradle.kts` (see diff) and synced:
- `com.google.android.gms:play-services-location:21.4.0` — geofencing + `ActivityRecognitionClient` (same artifact, no extra dependency needed if §9 gets built).
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `-android:1.11.0` — coroutines/Flow throughout.
- `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0` — `Task<T>.await()` for the Play Services APIs, instead of hand-rolling listener→continuation bridges.
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0` (test-only) — virtual time for the smoothing/timeout unit tests in §10.
- `com.google.dagger:hilt-android` / `hilt-android-compiler:2.60.1` + `com.google.devtools.ksp:2.2.10-2.0.2` — DI (superseded decision, see below).
- `com.jakewharton.timber:timber:5.0.1` — logging.

**Decision (updated): Hilt, not hand-rolled DI.** Originally planned hand-rolled DI to avoid setup risk (see struck-through reasoning below), but since the intent now is to build this as something meant to be extended rather than a one-shot exercise, paying Hilt's setup cost now — while it's still just a dependency-wiring step — beats retrofitting it after more classes exist. It also makes the fakes-for-prod swap in §10 mechanical via test-only `@Module`/`@TestInstallIn` bindings as the class count grows, rather than threading constructor params by hand.
~~Both are explicitly fine per the prompt. Given the timebox and half a dozen classes needing injection, Hilt's KSP setup/build-time cost isn't worth it — a couple of manual factory functions covers it with less risk of a build-config detour eating into coding time.~~

**Decision: Timber for logging.** Geofence/BLE bugs are timing-sensitive and frequently only reproducible on real hardware with the app backgrounded and no debugger attached (per the prompt's own setup-constraint warning) — `Timber.d(...)` piped to `adb logcat` is the practical way to see what the state machine did when there was no UI alive to show it. Trivial footprint, and a custom release `Tree` can mute/redirect it later if desired (not needed for this exercise).

**Build gotcha hit while wiring this up:** this project was relying on AGP 9's new "built-in Kotlin" support (no explicit `org.jetbrains.kotlin.android` plugin applied). KSP — which Hilt needs for code generation — doesn't yet support that mode; adding the Hilt/KSP plugins failed with `Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin` ([open upstream issue](https://github.com/google/ksp/issues/2729), unresolved as of Dec 2025). Fixed by opting back into the classic Kotlin Gradle plugin (`android.builtInKotlin=false` + `android.newDsl=false` in `gradle.properties`, plus explicitly applying `org.jetbrains.kotlin.android`), which then needed an explicit `jvmTarget` set to 11 to match `compileOptions` (the classic plugin doesn't auto-inherit it the way built-in Kotlin did). Google's docs warn this opt-out won't exist in AGP 10 — worth a one-line note in DECISIONS.md and a re-check whenever this project upgrades AGP past 9.

## 13. Open questions before coding starts

- ~~OK with the iBeacon identity choice (§3) and moving-average smoothing (§6), or prefer median/EMA?~~ — **Decided**: iBeacon + moving average, confirmed. See DECISIONS.md.
- ~~OK with "foreground service only while Inside" (§8 option 3)~~ — **Decided**: foreground-only-while-Inside, confirmed. See DECISIONS.md.

All three original open questions are now resolved. Remaining unconfirmed detail: the N=10s beacon-lost timeout in §6 is still a lean, not explicitly signed off.

## 14. Addendum — can this use a plain (non-foreground) background service?

Technically yes, but not advisable here, and the reasoning is worth spelling out since it's directly what requirement 9 is testing:

- A plain `Service` (`startService()`, no promotion to foreground) is only reliably alive while the app is itself in a foreground-equivalent state. The moment the app is fully backgrounded — no activity, screen off, phone in a pocket, which is the realistic "I walked into the venue" scenario this whole exercise is built around — Android's background execution limits (in effect since API 26) mean the system can and will stop it within a short window. On several OEMs (Samsung, Xiaomi, etc.) this is considerably more aggressive than stock Android.
- Independent of the service question, BLE scanning itself is throttled for apps not in the foreground (undocumented but real: roughly a handful of scan windows per 30s once backgrounded on stock Android, more aggressive on some OEM skins), plus Doze/App Standby suspending things further.
- Requirement 9 explicitly frames the problem as "a geofence transition can fire when your app has no UI alive" — that's precisely the condition under which a plain background service is least reliable. Since scanning needs to keep running for the entire time Inside a venue (which could be many minutes), not just react once, a background service risks silently going dark partway through — which would look like exactly the kind of bug the "must never be scanning while outside / must genuinely be scanning while inside" requirement is trying to catch, just in the opposite direction (silently *not* scanning while inside).
- Where a plain background service is fine: quick iteration/debugging with the screen on and the app foregrounded (e.g. testing the parsing/smoothing logic against a nearby simulator without worrying about the service lifecycle yet). Not what should ship, since the graded scenario (and the requested screen recording) is specifically the backgrounded case.

Net: it's possible to write, but foreground-while-Inside (§8 option 3) remains the recommendation — it's the one Android designed for "must keep doing something for an unpredictable duration, possibly while backgrounded."

## 15. Addendum — where should test fakes live?

This is a single `app` module (no library modules), so "module" here means package/source-set, not a separate Gradle module. Three real options:

| Option | Where | Tradeoff |
|---|---|---|
| Alongside impl | `data/geofence/GeofenceSourceFake.kt` next to the interface + real impl, in `main` | Easiest to keep in sync when the interface changes (one place to look), but it ships inside the production source set — with `optimization { enable = false }` currently set on the release build type (see `app/build.gradle.kts`), nothing strips it from the shipped APK, and it blurs "which of these is prod code" for a reader browsing the package. |
| `src/test/java/.../fakes/` | Own package, `test`-only source set | Matches Gradle's source-set semantics exactly — `testImplementation`-scoped, guaranteed excluded from the APK, very common convention. Limitation: `src/test` isn't visible from `src/androidTest` by default, so a fake here can't be reused by an instrumented test without extra wiring. |
| `testFixtures` source set | `src/testFixtures/java/...`, via AGP's `testFixtures { enable = true }` | The mechanism actually designed for "compiles against `main`, consumable by both `test` and `androidTest`, never shipped in the release artifact." Correct long-term answer if fakes need to be shared across unit and instrumented tests. Costs one extra source-set toggle + a `testFixturesImplementation` dependency wire-up. |

**Recommend `src/test/java/.../fakes/` for now.** All the testable logic this exercise calls out (containment, smoothing, beacon-lost timeout, the state machine including "EXIT stops scanning") is pure Kotlin exercised by JVM unit tests — nothing here needs an instrumented test, and the prompt's own simulation section confirms the BLE/geofence *integration* path can only be verified on real hardware anyway, not via `androidTest`. If that changes and an instrumented test later needs the same fake, promoting `test` → `testFixtures` is a small, mechanical move — not worth paying for upfront on a "since we're extending this" basis the way Hilt was, since the pure-JVM-unit-test path is what the actual grading criteria (§10) ask for.

**Verified empirically** (temporarily enabled `testFixtures { enable = true }`, added a trivial Kotlin fixture + throwaway unit/instrumented tests referencing it, then reverted everything — see DECISIONS.md for the summary): AGP 9.2.1 *does* generate a full `testFixtures` task graph for an **application** module (contradicts some docs/blog posts claiming library-only) — it even bundles a `testFixtures` AAR. But two real costs surfaced:
1. Kotlin sources under `src/testFixtures/java` are silently **not compiled** unless `android.experimental.enableTestFixturesKotlinSupport=true` is set in `gradle.properties` — no error, the class just doesn't exist, which is a confusing failure mode to debug from scratch.
2. Once that flag's on, this project's Compose compiler plugin (applied project-wide via `kotlin.compose`) runs on the new `compileDebugTestFixturesKotlin` task too, and fails outright — `IncompatibleComposeRuntimeVersionException: The Compose Compiler requires the Compose Runtime to be on the class path, but none could be found` — because `testFixturesImplementation` doesn't inherit the app's `implementation` deps. Fixable (add `testFixturesImplementation(libs.androidx.compose.ui)` etc.), but it means *any* testFixtures source set in this project needs Compose deps wired in just to satisfy the compiler plugin, even if the fixture itself never touches Compose.

Net: confirms §15's recommendation rather than changing it — `src/test/.../fakes/` needs zero extra flags or dependency duplication and already compiles cleanly (proven in §12's build gotcha work), while `testFixtures` costs one experimental flag plus Compose-dependency plumbing for no present benefit, since nothing here needs `androidTest` sharing yet.

## 16. Addendum — cost/effect of enabling release optimization (R8) now

Also verified empirically (temporarily flipped `optimization { enable = true }` on the release build type, ran a real `assembleRelease`, then reverted):

- On this exact AGP version (9.2.1), the simplified `optimization { enable = true }` DSL — already the syntax this project's template uses, just set to `false` — isn't fully live yet: it failed configuration with `Cannot use optimization.enable=true without setting android.r8.gradual.support flag`. Per Google's docs, the one-line DSL is only fully supported starting AGP 9.3; on 9.2.1 it's gated behind `android.r8.gradual.support=true` in `gradle.properties`.
- **Separately discovered, not caused by this or the Hilt/Timber additions (since fixed):** release builds and `androidTest` compiles were failing at `checkReleaseAarMetadata`/`checkDebugAarMetadata` — `androidx.core:core-ktx:1.19.0`, `androidx.core:core:1.19.0`, and `androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0` all require compiling against API 37, but `compileSdk` was pinned at `release(36) { minorApiLevel = 1 }`. Bumped to `compileSdk = release(37)` during the §17 scaffolding pass — `checkDebugAarMetadata` now passes clean.
- With both flags set (and that check skipped just to measure), real numbers on the current template-sized codebase: **unoptimized** release APK (unsigned) = 23.7 MB, build ≈ 26s. **Optimized** (R8 minify + shrink) release APK = 1.69 MB, build ≈ 29s (first run after a config change, so includes reconfiguration overhead; steady-state incremental rebuilds would be faster). No `missing_rules.txt` and no R8 warnings — Hilt's, Play Services Location's, and Timber's bundled consumer ProGuard rules handled the current dependency set cleanly. That's not a guarantee it stays warning-free once real reflection-touching app code exists, but it's a clean starting point.
- Cost of turning it on now, beyond the one flag: none technical, but a real workflow tradeoff — minified builds are harder to debug (stack traces need `mapping.txt` to de-obfuscate, and anything reflection-based that consumer rules don't cover fails silently at runtime rather than at compile time, which is a worse failure mode to chase down mid-exercise). The project's release build type was deliberately left `enable = false`; that's the normal posture during active development — flip it on right before the final build/APK you'd actually ship, not now while the app doesn't exist yet.

**Recommendation:** leave `optimization.enable = false` as-is for now (not changed). Revisit right before wrapping up, and budget a few minutes to smoke-test the release build once real code exists, since minification failures are easiest to catch early rather than discovering them right before submission.

## 17. Scaffolding pass — module shape + Hilt wiring

Executed §2's package layout (with §15's fake placement: `src/test/.../fakes/`, not alongside impl) as compiling stub code — interfaces, data classes, DI wiring, and manifest entries in place; business logic left as `TODO()`s pointing back at the relevant section above (§4 containment, §5 receiver, §6 RSSI/BLE parsing, §7 reducer, §8 service start/stop). Created:

- `data/venue` — `Venue`, `BeaconIdentity` (iBeacon shape), `VenueRepository` (2 hardcoded placeholder venues, `0.0,0.0` — real mockable coordinates still needed for README)
- `domain` — `GeoPoint` (framework-free per §10), `VenueState`/`Proximity`/`VenueEvent`, `ContainmentChecker`, `RssiSmoother`, `BeaconLostTimer`, `VenueStateMachine` — all stub bodies
- `data/geofence`, `data/ble`, `data/location` — interface + real (stub) impl pairs (`PlayServicesGeofenceSource`, `PlatformBleScanner`, `FusedLocationSource`)
- `service` — `GeofenceBroadcastReceiver`, `VenueMonitorService`, both `@AndroidEntryPoint`
- `di` — `VenueCheckInApplication` (`@HiltAndroidApp`), `DataModule` (`@Binds` for all three interfaces)
- `ui` — `VenueViewModel` (`@HiltViewModel`, real `MutableStateFlow<VenueState>` seeded `Outside`), `VenueScreen` (placeholder, collects state into a `Text`); `MainActivity` wired to it
- `fakes/` (`src/test/...`) — `FakeGeofenceSource`, `FakeBleScanner`, `FakeLocationSource`: fully functional (not stubbed), `Flow`-backed, scriptable from tests
- `AndroidManifest.xml` — application name, §11's permission block, receiver/service registration (`foregroundServiceType="connectedDevice"`)

Two deviations from the original spec, both kept: added `androidx.hilt:hilt-navigation-compose` (needed for `hiltViewModel()` in Compose, missing from the original dependency list) and bumped `compileSdk` `36.1 → 37`, resolving the pre-existing `checkDebugAarMetadata` failure noted in §16 above. Verified with a clean `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` — first real exercise of Hilt's KSP codegen in this project, passed without needing further fixes.
