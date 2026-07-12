# Android Coroutine Storm

[![Codecov](https://codecov.io/gh/santimattius/android-coroutine-storm/branch/master/graph/badge.svg?token=HNW9TXKMQU)](https://codecov.io/gh/santimattius/android-coroutine-storm)
[![Quality Checks](https://github.com/santimattius/android-coroutine-storm/actions/workflows/main.yml/badge.svg)](https://github.com/santimattius/android-coroutine-storm/actions)
[![AGP 9.2.1](https://img.shields.io/badge/AGP-9.2.1-blue.svg)](https://developer.android.com/build/releases/gradle-plugin)
[![Kotlin 2.4.0](https://img.shields.io/badge/Kotlin-2.4.0-purple.svg)](https://kotlinlang.org/docs/whatsnew20.html)

A hands-on PoC that demonstrates **coroutine storms**: what happens when an unbounded fan-out of coroutines floods `Dispatchers.Default`/`Dispatchers.IO` and starves the main thread of CPU cores, even though no coroutine ever touches the main thread directly. The project started from a production-ready Compose/Hilt skeleton and now ships the full storm lab described in [`docs/poc-coroutine-storm.md`](docs/poc-coroutine-storm.md): live `queueDelay`/concurrency instrumentation, JankStats jank counting, an App Startup–driven storm, and a Macrobenchmark suite that measures the damage on a real device.

## 🎯 What you can see and measure

1. **Visible jank**: an infinite spin animation in Compose that stutters the moment you unleash an unbounded coroutine fan-out — and recovers instantly once the same work runs through a bounded (`Semaphore`-style) path.
2. **Live instrumentation**: `queueDelay` (time a coroutine sits ready-but-unscheduled) and peak concurrency for both `Dispatchers.Default` and `Dispatchers.IO`, plus a running JankStats frame counter — all in `StormScreen`'s metrics panel.
3. **Reproducible measurement**: a dedicated `:macrobenchmark` module comparing cold-start TTID and frame timings between the `calm` and `storm` product flavors.

## 🚀 Key Features

- **Modern Tech Stack**: Jetpack Compose, Hilt, Coroutines, and Flow.
- **Coroutine Storm Lab**: `InstrumentedDispatcher`, `StormLab` (unbounded vs. bounded fan-out), and a live `StormMetrics` panel — see [Coroutine Storm Lab](#-coroutine-storm-lab) below.
- **App Startup Integration**: an `androidx.startup` `Initializer` reproduces the storm before the first frame, gated by build flavor for reproducible benchmarking.
- **Macrobenchmark Suite**: `StartupTimingMetric`/`FrameTimingMetric` comparisons between `calm` and `storm` builds.
- **Built-in Quality Control**: Detekt for static analysis (zero-issues gate) and Jacoco for code coverage.
- **AGP 9.0+ Ready**: built-in Kotlin support and the new DSL interfaces.
- **CI/CD Integrated**: pre-configured GitHub Actions for automated testing and secret management.

## 🏗 Architecture & Design

- **Dependency Injection**: Hilt for compile-time safe DI, including a qualified `@AppScope CoroutineScope` singleton shared by the app and the App Startup `Initializer` (via `EntryPointAccessors`, not a manual singleton — see the storm lab's `di` package).
- **UI Framework**: 100% Jetpack Compose for a reactive UI.
- **Asynchronous Programming**: Kotlin Coroutines and Flow for seamless data handling.
- **Image Loading**: Coil for efficient image loading in Compose.

## 🛠 Project Structure

```text
├── app/
│   ├── src/main/java/com/santimattius/basic/skeleton/
│   │   ├── storm/
│   │   │   ├── instrumentation/  # InstrumentedDispatcher, JankReporter, StormMetrics
│   │   │   ├── lab/              # StormLab: unbounded (❌) vs. bounded (✅) fan-out
│   │   │   ├── startup/          # StormInitializer (androidx.startup)
│   │   │   ├── di/               # AppScope qualifier, StormModule, StormEntryPoint
│   │   │   └── ui/               # StormScreen, StormViewModel
│   │   └── MainActivity.kt       # Hosts StormScreen + JankStats tracking
│   ├── src/test/                 # Unit tests for InstrumentedDispatcher/StormLab (real dispatchers)
│   ├── src/androidTest/          # Hilt EntryPoint androidTest
│   └── build.gradle.kts          # calm/storm flavors, benchmark build type
├── macrobenchmark/                # com.android.test module: StartupBenchmark, FrameTimingBenchmark
├── config/                        # Detekt configuration
├── gradle/
│   └── libs.versions.toml         # Centralized dependency management
├── docs/
│   └── poc-coroutine-storm.md     # Full write-up this PoC implements
└── plugins/                        # Custom build plugins
```

## 🧪 Coroutine Storm Lab

Two product flavors control whether the storm fires automatically before the first frame:

| Flavor | `STORM_AT_STARTUP` | Use for |
| :--- | :--- | :--- |
| `calm` | `false` | Baseline — no fan-out at startup. |
| `storm` | `true` | `StormInitializer` fans out 2,000 coroutines onto `Dispatchers.Default` before `Application.onCreate()`. |

Inside `StormScreen`, two buttons let you trigger the same comparison manually at any time:

- **❌ Unleash storm (unbounded)** — 2,000 coroutines launched directly on `Dispatchers.Default`. Watch the spin animation stutter and `queueDelay`/jank counters spike.
- **✅ Run structured (bounded concurrency)** — the same amount of work, capped to a fixed worker pool, keeping the UI smooth.

## 🚦 Getting Started

### Prerequisites
- Android Studio Ladybug | 2024.2.1 or newer
- JDK 17 (configured in Gradle settings)

### Run the app
```bash
./gradlew :app:installCalmDebug   # baseline, no startup storm
./gradlew :app:installStormDebug  # fan-out fires before the first frame
```
Or just pick the `calmDebug`/`stormDebug` variant in Android Studio's **Build Variants** panel and hit Run.

### Verification & Testing

Run project-wide quality checks:
```bash
./gradlew check
```

Execute unit tests (TDD-covered: `InstrumentedDispatcher`, `StormLab.structured`'s concurrency bound, `StormMetrics`, `JankReporter`):
```bash
./gradlew test
```

Run static analysis (Detekt — zero issues allowed, no baseline):
```bash
./gradlew :app:detekt :macrobenchmark:detekt
```

### Macrobenchmark (requires a physical device or emulator)

Macrobenchmark uses its own build type/module and needs the target app installed explicitly before running:
```bash
./gradlew :app:installCalmBenchmark :macrobenchmark:connectedCalmBenchmarkAndroidTest
./gradlew :app:installStormBenchmark :macrobenchmark:connectedStormBenchmarkAndroidTest
```
> ⚠️ Don't run `connected*DebugAndroidTest` for benchmarking — AGP always keeps an implicit `debug` build type around, and a debuggable target makes Macrobenchmark fail with a `DEBUGGABLE` error. Use the `*Benchmark` variants above.

Compare `timeToInitialDisplayMs` between the two `StartupBenchmark` runs, then check `FrameTimingBenchmark`'s frame-duration histogram while the storm competes for CPU cores during the animation.

### Code Coverage Reports

Generate Jacoco coverage for debug builds:
```bash
./gradlew :app:testDebugUnitTestCoverage
```
*Reports are generated at: `app/build/reports/jacoco/`*

## 🔐 Configuration & Secrets

### Local Secret Management
The project uses the `secrets-gradle-plugin`. To define API keys or sensitive data:

1. Add your key to `local.properties`:
   ```properties
   apiKey="your_api_key_here"
   ```
2. Access it in code:
   ```kotlin
   val apiKey = BuildConfig.apiKey
   ```

### AGP 9.0 Note
`buildConfig` is explicitly enabled in `app/build.gradle.kts` to maintain compatibility with modern plugin standards, and is also required for the `STORM_AT_STARTUP`/`TARGET_APP_ID` fields used by the storm lab and the Macrobenchmark module.

## 📦 Dependencies

| Category | Libraries |
| :--- | :--- |
| **UI** | Jetpack Compose (BOM), Material 3, Coil |
| **DI** | Hilt |
| **Async** | Coroutines, Flow |
| **Networking** | Retrofit, Gson, OkHttp |
| **Coroutine Storm Lab** | `androidx.metrics:metrics-performance` (JankStats), `androidx.startup:startup-runtime`, `androidx.profileinstaller:profileinstaller` |
| **Benchmarking** | `androidx.benchmark:benchmark-macro-junit4`, `androidx.test.uiautomator` |
| **Testing** | JUnit 4, kotlinx-coroutines-test, MockWebServer, Compose Test, Hilt Android Testing |

---
Maintainer: [Santiago Mattiauda](https://github.com/santimattius)
