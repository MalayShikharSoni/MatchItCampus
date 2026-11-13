# HANDOFF

The memory between work sessions. Every session starts by reading this file in full.
Keep it short and accurate. Never delete past decisions; mark them **superseded** instead.

## 1. Status

From Phase 2 on, all work is committed and pushed directly to `main` (see the
"Git workflow" decision in section 3). There are no phase branches or PRs.

| Phase | Driver | Branch | PR | On `main` |
|---|---|---|---|---|
| 0 Environment check | Dev A (MalayShikharSoni) | — (no code) | — | n/a |
| 1 Scaffold | Dev A (MalayShikharSoni) | `feature/01-scaffold` (merged, last phase branch) | [#1](https://github.com/MalayShikharSoni/MatchItCampus/pull/1) | yes |
| 2 Room data layer | Dev B | `main` | — | |
| 3 Firebase + sync | Dev A | `main` | — | |
| 4 Matching engine | Dev B | `main` | — | |
| 5 Core UI | Dev A | `main` | — | |
| 6 Location + map | Dev B | `main` | — | |
| 7 Polish + migration | Dev A | `main` | — | |
| 8 Tests + docs | Dev B | `main` | — | |

## 2. Current state

**What works (end of Phase 1):**
- The project builds (`./gradlew assembleDebug`) and unit tests pass (`./gradlew testDebugUnitTest`).
- Package / applicationId `com.matchitcampus.app`, minSdk 26, compile/target SDK 37.
- `MatchItApp` (Application) builds an `AppContainer` holding the app-wide `applicationScope`.
- Material 3 theme: dynamic colour on Android 12+, custom blue/teal/amber fallback palette,
  light and dark, system-bar icons synced through `SideEffect`.
- Navigation shell with type-safe routes (`ui/navigation/Routes.kt`):
  - Signed out: Login → Register (placeholders). "Continue" fakes a sign-in.
  - Signed in: Home, Map, My posts, Claims, Profile tabs, plus Post item, Item detail
    (receives `itemId`) and Debug tools (debug builds only). All screens are placeholders.
  - Bottom `NavigationBar` under 600 dp width, side `NavigationRail` from 600 dp
    (`navigationSuiteTypeFor`, unit-tested).
- MediaPipe model `app/src/main/assets/universal_sentence_encoder.tflite` is committed (not used yet).

**How to run:** open the project root in Android Studio, make sure `app/google-services.json`
exists (see section 5), then Run ▶ on an emulator or device.

## 3. Decisions and deviations from the brief

- **Git workflow: work only on `main` (decided 2026-11-13, after Phase 1).** This supersedes
  the brief's per-phase `feature/NN-*` branches and PR review/merge (brief 11.3 step 5, 11.5).
  Each phase now: `git checkout main && git pull`, work and commit on `main`, run the
  attribution check (brief 11.6) against `origin/main..HEAD`, then `git push origin main`.
  No PRs and no review step. GitHub does not enforce reviews (`main` is unprotected).
  Everything else in 11.4 and 11.6 still applies: the laptop's own git identity, small
  conventional commits, no AI attribution, and never committing `MATCHIT_CAMPUS_PROMPT.md`.
  Always pull before starting, so the two laptops don't diverge.
- **Repo setup (Phase 1):** the Android Studio project was created first and then linked
  to the GitHub repo (`git init`, `git remote add origin …`, `git pull origin main`)
  instead of cloning. The result is the same: the project sits in the repo root next to `README.md`.
- **`MATCHIT_CAMPUS_PROMPT.md`** was found in `app/` during Phase 0 and moved to the
  project root, as the brief requires. It is git-ignored.
- **AGP 9 "built-in Kotlin":** AGP 9 compiles Kotlin itself, so there is **no**
  `org.jetbrains.kotlin.android` plugin. The Compose compiler plugin (2.4.21) puts
  KGP 2.4.21 on the build classpath, which overrides AGP's default KGP 2.2.10
  (verified with `./gradlew buildEnvironment`: `2.2.10 -> 2.4.21`). The Kotlin JVM target
  follows `compileOptions` (Java 17), so there is no `kotlinOptions` block.
- **KSP 2.3.x** is versioned independently of Kotlin (it no longer looks like `2.x.y-1.0.z`).
  The plugin is applied now and gets its first processor (Room) in Phase 2.
- **Gradle wrapper 9.8.2.** AGP 9.5 needs Gradle 9.6.0 or newer. `gradle-wrapper.properties`
  was edited by hand (URL + SHA-256 from services.gradle.org) because `./gradlew wrapper`
  failed its network check on this laptop.
- **Adaptive navigation:** `NavigationSuiteScaffold` from `material3-adaptive-navigation-suite`,
  with our own width-only rule instead of the library default, because the default shows a
  bottom bar on a landscape phone (compact height). Uses `currentWindowAdaptiveInfoV2()`
  (the non-V2 function is deprecated).
- **Icons:** `material-icons-core` (from the Compose BOM). The extended icon set is avoided
  because it's very large.
- **Phase 1 placeholders:** screens have no ViewModels yet, so there is no `XxxRoute`/`XxxScreen`
  split yet. Each phase adds the split as it builds the real screen.
  `MatchItRoot` uses a `rememberSaveable` "signed in" flag until Phase 3 replaces it with
  `AuthRepository.authState`.
- **`.idea/` is fully git-ignored** to avoid IDE-file merge conflicts between the two laptops.
- **`.claude/` is fully git-ignored (2026-11-13).** Local tool settings stay on each laptop and
  are shared directly, like the brief. This supersedes Phase 1, where one settings file was committed.
- **Contributors:** Dev A is MalayShikharSoni (repo owner). Dev B is added as a GitHub collaborator,
  and on any other laptop the configured git identity is Dev B's. No contributor table is kept.
- **`.gitattributes`** added: LF for `gradlew`, CRLF for `.bat`, binary for `.tflite`/`.jar`/images.

### Library versions

Checked on Google Maven / Maven Central on 2026-11-05. "In use" means it's already in
`gradle/libs.versions.toml`. Re-check the "later" ones when their phase starts, especially
MapLibre Compose, which is pre-1.0.

| Library | Version | Status |
|---|---|---|
| Android Gradle Plugin | 9.5.0 | in use |
| Gradle | 9.8.2 | in use |
| Kotlin (+ Compose compiler & serialization plugins) | 2.4.21 | in use |
| KSP | 2.3.13 | in use (plugin only) |
| google-services plugin | 4.5.0 | in use |
| Compose BOM | 2026.09.00 (material3 1.4.0, adaptive 1.3.0, ui 1.12.1) | in use |
| Navigation Compose | 2.10.2 | in use |
| Lifecycle | 2.11.0 | in use |
| Activity Compose | 1.13.0 | in use |
| Core KTX | 1.19.1 | in use |
| kotlinx-serialization-json | 1.12.0 | in use |
| kotlinx-coroutines | 1.11.0 | in use (android); add `-test` later |
| Room | 2.8.5 | Phase 2 |
| DataStore Preferences | 1.3.0 | Phase 2/3 |
| Firebase BoM | 35.1.0 (Auth 25.0.0, Firestore 27.1.0) | Phase 3 |
| MediaPipe tasks-text | 1.2.2 | Phase 4 |
| Play Services Location | 21.4.0 | Phase 6 |
| MapLibre Compose | 0.20.0 (minSdk 24) | Phase 6 |
| Coil 3 (`io.coil-kt.coil3:coil-compose`) | 3.6.3 | Phase 5 |
| Turbine | 1.2.1 | tests |
| JUnit 4 / AndroidX test JUnit / Espresso | 4.13.2 / 1.3.0 / 3.7.0 | in use |

## 4. Known issues / TODOs

- The merged branch `feature/01-scaffold` still exists on GitHub. It's safe to delete it
  (GitHub's "Delete branch" button on PR #1).
- No tablet emulator exists on Dev A's laptop yet. Create one before Phase 7 (two-pane layout).
- The launcher icon is still the Android Studio default.

## 5. Per-laptop setup notes

Things each laptop needs that are **not** in the repo:
- `MATCHIT_CAMPUS_PROMPT.md` in the project root (shared directly, git-ignored).
- `app/google-services.json` from the Firebase console (project `matchit-campus`, Android app
  `com.matchitcampus.app`). Git-ignored. Without it the build fails at `processDebugGoogleServices`.
- JDK 17+ (Android Studio's bundled JDK is fine) and Android SDK platform 37.
- The `.claude/` folder in the project root (shared directly, git-ignored).

## 6. Next phase

**Phase 2: Room data layer**, driven by **Dev B**, working directly on `main`.
- Dev B clones the repo, copies in `MATCHIT_CAMPUS_PROMPT.md`, `.claude/` and
  `app/google-services.json`, and checks their own git identity.
- Don't create a `feature/02-room` branch or a PR (see the Git workflow decision in section 3).
- Add Room 2.8.5 (runtime, ktx, compiler via `ksp(...)`, testing). Set the schema location to
  `app/schemas/` (Room Gradle plugin or KSP arg) with `exportSchema = true`.
- `AppContainer` is where the database should be built (`by lazy`), using `applicationContext`.
- Domain enums go in `domain/model/`; Room code goes in `data/local/`.
