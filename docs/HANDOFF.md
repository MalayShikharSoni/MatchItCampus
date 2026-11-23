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
| 2 Room data layer | Dev B (anugyajais) | `main` | — | yes |
| 3 Firebase + sync | Dev A (MalayShikharSoni) | `main` | — | yes |
| 4 Matching engine | Dev B | `main` | — | |
| 5 Core UI | Dev A | `main` | — | |
| 6 Location + map | Dev B | `main` | — | |
| 7 Polish + migration | Dev A | `main` | — | |
| 8 Tests + docs | Dev B | `main` | — | |

## 2. Current state

**What works (end of Phase 3):**
- The project builds (`./gradlew assembleDebug`) and unit tests pass (`./gradlew testDebugUnitTest`).
- Package / applicationId `com.matchitcampus.app`, minSdk 26, compile/target SDK 37.
- `MatchItApp` (Application) builds an `AppContainer` holding the app-wide `applicationScope`.
- Material 3 theme: dynamic colour on Android 12+, custom blue/teal/amber fallback palette,
  light and dark, system-bar icons synced through `SideEffect`.
- Navigation shell with type-safe routes (`ui/navigation/Routes.kt`):
  - Signed out: real Login and Register screens (Firebase Auth email/password, Phase 3).
  - Signed in: Home, Map, My posts, Claims, Profile tabs, plus Post item, Item detail
    (receives `itemId`) and Debug tools (debug builds only). All screens are placeholders.
  - Bottom `NavigationBar` under 600 dp width, side `NavigationRail` from 600 dp
    (`navigationSuiteTypeFor`, unit-tested).
- MediaPipe model `app/src/main/assets/universal_sentence_encoder.tflite` is committed (not used yet).
- **Room database v1** (`data/local/`): tables `items`, `campus_zones`, `claims`, `users`, `matches`;
  DAOs (Flow for observed queries, suspend for writes, `@Upsert`); relations `ItemWithZone` and
  `ClaimWithItems`; `Converters` (enums by name, `FloatArray` ↔ little-endian BLOB). The schema is
  exported to `app/schemas/com.matchitcampus.app.data.local.AppDatabase/1.json` (committed).
- Domain enums and models in `domain/model/`, with entity ↔ domain mappers in `data/local/mapper/`.
- **Zones:** `assets/campus_zones.json` holds 17 placeholder zones (all at 31.2550, 75.7050,
  `isPlaceholder: true`). `ZoneRepository.seedIfEmpty()` runs from `MatchItApp.onCreate` and
  seeds them on first launch (Logcat: `Seeded 17 campus zones`).
- **Local repositories** in `AppContainer`: `ItemRepository`, `ClaimRepository`, `ZoneRepository`.
  Writes are marked `PENDING_UPLOAD` / `PENDING_DELETE`. Nothing reaches Firestore yet.
- Tests (Phase 2): JVM unit tests (`./gradlew testDebugUnitTest`) and instrumented tests on an
  in-memory DB (`./gradlew connectedDebugAndroidTest`).
  The feed, map, posts and claims screens are still Phase 1 placeholders and don't read the database yet.
- **Auth (Phase 3):** `AuthRepository` wraps Firebase Auth. `authState` is a `StateFlow<AuthUser?>`
  built with `callbackFlow` + `AuthStateListener`. `RootViewModel` switches between the auth graph
  and the main graph from it. Registration creates `users/{uid}` and caches the profile in Room.
  Login/Register validate in the ViewModel (`AuthValidator`) and map Firebase errors to messages.
- **Sync (Phase 3):** `data/sync/SyncManager` (started in `MatchItApp`). It pushes `PENDING_UPLOAD` /
  `PENDING_DELETE` rows whenever Room's unsynced count is > 0 and the network is up, and while
  signed in **and** in the foreground it listens to items changed since the last sync plus the
  user's claims. Logcat: `tag:MatchIt/SyncManager`.
- **Profile (Phase 3):** shows name/email/reg. no. and sync status; Log out warns with an
  `AlertDialog` if there are unsynced changes, then clears claims and the cached profile.
- **Debug tools (Phase 3):** "Force sync" and "Post a test FOUND item" (to check an item
  reaches the Firestore console before the real Post screen exists).
- **Security rules:** `firebase/firestore.rules`. Must be pasted into the Firebase console
  (Firestore → Rules → Publish) before anything can sync.
- Tests: 44 JVM unit tests and 35 instrumented tests (both suites green on 2026-11-23).

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
- **AGP 9.2.0 instead of 9.5.0 (2026-11-23, Phase 2 start, Dev B's laptop).** Supersedes the
  9.5.0 choice from Phase 1. Dev B's Android Studio (build 253, 2025.3) can't sync AGP 9.5.
  `assembleDebug` and `testDebugUnitTest` pass on 9.2.0, which still works with Gradle 9.8.2.
  Move back up only when both laptops' Android Studio versions support the newer AGP.
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

- **Phase 2, Room plugin for the schema location:** the `androidx.room` Gradle plugin
  (`room { schemaDirectory("$projectDir/schemas") }`) instead of a KSP argument. It works with
  AGP 9.2 + built-in Kotlin.
- **Phase 2, explicit enum converters:** Room 2.3+ can store enums by itself, but `Converters`
  writes them out so the stored format (the constant name) is visible. Renaming an enum constant
  breaks stored rows, so treat the names as part of the database format.
- **Phase 2, no foreign keys** between items, zones and claims, because Firestore rows arrive in
  any order and a zone id may be unknown on a phone. Relations just return null instead.
- **Phase 2, `ItemEntity` overrides `equals`/`hashCode`** to compare the embedding array by content.
- **Phase 2, the domain `Item` has no embedding.** `ItemRepository.saveItem()` clears the stored
  embedding (the text may have changed), so Phase 4's `ensureEmbeddings()` refills it. Status
  changes use targeted `UPDATE` queries, which keep the embedding.
- **Phase 2, zone seeding uses a `count() == 0` check**, not `RoomDatabase.Callback`. A failed seed
  retries on the next launch, and seeding never overwrites zones that are already in the table.
  As a result, edits to `campus_zones.json` only reach a phone after a fresh install or cleared data.
  The JSON has a `_readme` key (ignored by the parser) explaining how to edit it.
- **Phase 2, "Other / Not sure" zone** has a 120 m radius like the other large areas.
- **Phase 2, claim rules are already in `ClaimRepository`** (ahead of the Phase 5 claims UI):
  `createClaim` (PENDING only, not on your own item; it doesn't touch the finder's item),
  `acceptClaim` (one `withTransaction`: claim ACCEPTED, found item RETURNED, other pending claims
  REJECTED), `rejectClaim` and `cancelClaim` (only while PENDING), and `clearAll` for logout.
  The claimant's linked lost item is **not** changed here, because it's the claimant's document.
- **Phase 2, no `UserRepository` and no `MatchRepository` yet.** `UserDao` and `MatchDao` exist
  for Phase 3 (AuthRepository) and Phase 4 (MatchRepository).
- **Phase 2, repositories take a `clock: () -> Long`** (and `ZoneRepository` takes a
  `readSeedJson: () -> String`), so tests can fix the time and the file contents.

- **Phase 3, Firestore document shape:** every item/claim document has `updatedAt` (server
  timestamp, enforced by the rules to equal `request.time`) **and** `updatedAtMillis` (the phone
  clock). The server time drives incremental sync ("changed since"), because a wrong phone clock
  can't skew it. The phone time drives last-write-wins against Room, which uses the same clock.
- **Phase 3, deletes are soft (deviation from the brief).** Deleting an item writes
  `deleted: true` (a tombstone) instead of removing the document. A removed document would never
  reach phones that already have the item, because they only ask for "changed since". The owner's
  phone removes its row once the tombstone is written. `update()` on a missing document fails with
  NOT_FOUND, which means the item was never uploaded, so the local row is just removed (this
  resolves the Phase 2 TODO about never-uploaded deletes).
- **Phase 3, merge rule:** `SyncMerge` (pure, unit-tested). Remote wins if its `updatedAtMillis` >= the
  local row's `updatedAt`, otherwise the local row is kept (so a newer `PENDING_UPLOAD` is never
  overwritten). Embeddings and the local photo path are preserved when the text hasn't changed.
- **Phase 3, push trigger:** `SyncManager` observes `observeUnsyncedCount()` (items + claims) and
  `ConnectivityObserver.isOnline` with `combine` + `debounce(500)`. Repositories don't call
  `SyncManager`, which avoids a dependency cycle. `markSynced(id, updatedAt)` only marks the row if
  it wasn't edited during the upload. Rows owned by another account are skipped.
- **Phase 3, incremental item listener:** `whereGreaterThan(updatedAt, lastSync).orderBy(updatedAt)
  .limit(200)`. When a page comes back full, the listener re-attaches from the new sync point.
  `lastItemSyncMillis` is in DataStore (`data/prefs/SyncPreferences`). Docs with pending writes
  don't move the sync point. Because the stored point is in millis and server timestamps have
  microseconds, the newest doc is re-read once per attach (1 read), which is harmless.
- **Phase 3, lifecycle:** `combine(authState uid, ProcessLifecycleOwner STARTED)` + `collectLatest`.
  A change cancels the session coroutine, and the listeners' `awaitClose` removes them.
- **Phase 3, auth work runs in `applicationScope`** (`externalScope.async { }.await()`): a successful
  sign-in swaps the screen and cancels the login ViewModel's scope, which would otherwise cut off
  the profile save. Logout waits for `authState == null` before clearing claims, and
  `SyncManager.applyClaims` checks the uid inside its transaction, so a late snapshot can't
  re-insert private claims.
- **Phase 3, found item status follows claims on the finder's phone**
  (`ClaimRepository.refreshFoundItemStatus`): OPEN → CLAIM_PENDING when a pending claim exists, and
  back to OPEN when none are left (the brief didn't say what happens after a rejection).
  `rejectClaim` calls it too. The claimant's phone marks its linked lost item RETURNED when it sees
  an ACCEPTED claim.
- **Phase 3, rules allow an optional `color` field on items** already, so the Phase 7 v2 migration
  doesn't need a rules change at the same moment.
- **Phase 3, rules were tested** against the local Firestore emulator (39 allow/deny checks covering
  every write the app makes and the main attacks). The test script isn't in the repo yet; Phase 8
  can add it (Node + `@firebase/rules-unit-testing`) if wanted.
- **Phase 3, no ViewModel unit tests for Login/Register:** `AuthRepository` is a concrete class over
  Firebase. The validation rules are unit-tested (`AuthValidatorTest`). Phase 8's required ViewModel
  tests are for PostItem and Home.

### Library versions

Checked on Google Maven / Maven Central on 2026-11-05. "In use" means it's already in
`gradle/libs.versions.toml`. Re-check the "later" ones when their phase starts, especially
MapLibre Compose, which is pre-1.0.

| Library | Version | Status |
|---|---|---|
| Android Gradle Plugin | 9.2.0 (was 9.5.0, see decisions) | in use |
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
| kotlinx-coroutines (android, test) | 1.11.0 | in use |
| Room (runtime, ktx, compiler via KSP, testing, Gradle plugin) | 2.8.5 | in use (re-checked 2026-11-23) |
| DataStore Preferences | 1.3.0 | in use (Phase 3) |
| Firebase BoM | 35.2.0 (Auth 25.0.0, Firestore 27.2.0) | in use (re-checked 2026-11-23) |
| kotlinx-coroutines-play-services | 1.11.0 | in use (`Task.await()`) |
| Lifecycle process (`ProcessLifecycleOwner`) | 2.11.0 | in use |
| MediaPipe tasks-text | 1.2.2 | Phase 4 |
| Play Services Location | 21.4.0 | Phase 6 |
| MapLibre Compose | 0.20.0 (minSdk 24) | Phase 6 |
| Coil 3 (`io.coil-kt.coil3:coil-compose`) | 3.6.3 | Phase 5 |
| Turbine | 1.2.1 | in use (androidTest) |
| JUnit 4 / AndroidX test JUnit / Espresso | 4.13.2 / 1.3.0 / 3.7.0 | in use |
| AndroidX test core / runner | 1.7.0 / 1.7.0 | in use (androidTest) |

## 4. Known issues / TODOs

- The merged branch `feature/01-scaffold` still exists on GitHub. It's safe to delete it
  (GitHub's "Delete branch" button on PR #1).
- No tablet emulator exists on Dev A's laptop yet. Create one before Phase 7 (two-pane layout).
- The launcher icon is still the Android Studio default.
- `ZoneSeedParserTest.realSeedFile_isStillAllPlaceholders` must be updated once real zone
  coordinates are captured (Phase 6).
- ~~Deleting an item that was never uploaded still waits for sync.~~ Resolved in Phase 3 (see decisions).
- `SyncManager.events` (a `SharedFlow`, e.g. "new claim on your item") is emitted but not shown yet.
  Phase 5 should collect it with `LaunchedEffect` and show a snackbar.
- Photos (`itemPhotos/{itemId}`) aren't uploaded yet (Phase 5). The rules require the item document
  to exist first, so upload the item, then the photo.
- Last-write-wins uses phone clocks, so two phones with very different clocks can pick the wrong
  winner. It's acceptable because each document normally has a single writer (its owner).
- If someone logs out with unsynced changes and another account logs in on the same phone, the
  old rows stay `PENDING_UPLOAD` (they're skipped, because the rules would reject them).

## 5. Per-laptop setup notes

Things each laptop needs that are **not** in the repo:
- `MATCHIT_CAMPUS_PROMPT.md` in the project root (shared directly, git-ignored).
- `app/google-services.json` from the Firebase console (project `matchit-campus`, Android app
  `com.matchitcampus.app`). Git-ignored. Without it the build fails at `processDebugGoogleServices`.
- JDK 17+ (Android Studio's bundled JDK is fine) and Android SDK platform 37.
- Dev B's laptop: Android Studio 2025.3 (build 253), hence AGP 9.2.0. AVD `Pixel_9_Pro` is used
  for the connected tests.
- The `.claude/` folder in the project root (shared directly, git-ignored).
- **Dev A's network has Sophos SSL inspection.** Now and then it replaces Google's certificate with
  its own, which Java (Gradle) and Node don't trust, so downloads fail with `PKIX path building
  failed`. Retrying works because Gradle caches what it already downloaded. A permanent fix is to
  import the Sophos CA into the JDK truststore, or to use another network.

## 6. Next phase

**Phase 4: Matching engine**, driven by **Dev B**, working directly on `main`.
- `git checkout main && git pull` first. Phase 3 is on `main`.
- Before testing sync with real accounts, the rules in `firebase/firestore.rules` must be
  published in the Firebase console (Dev A does this).
- Re-check MediaPipe `tasks-text` (1.2.2 on 2026-11-05) before adding it. The model is already in
  `app/src/main/assets/universal_sentence_encoder.tflite`.
- Embeddings are local only. Remote items arrive in `SyncManager.applyItemPage` with `embedding`
  kept when the text is unchanged and null otherwise. `MatchRepository.ensureEmbeddings()` should
  fill the nulls: call it after `applyItemPage` and after local saves (or observe items with a
  null embedding), and recompute matches for the affected items.
- `ItemRepository.saveItem` clears the embedding on every save (Phase 2 decision). Status-only
  changes use `updateStatus` and keep it.
- `AppContainer` is where `TextEmbedderHelper` and `MatchRepository` should be built (`by lazy`).
