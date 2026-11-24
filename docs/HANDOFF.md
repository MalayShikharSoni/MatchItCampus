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
| 4 Matching engine | Dev B (anugyajais) | `main` | — | yes |
| 5 Core UI | Dev A (MalayShikharSoni) | `main` | — | yes |
| 6 Location + map | Dev B (anugyajais) | `main` | — | yes |
| 7 Polish + migration | Dev A | `main` | — | |
| 8 Tests + docs | Dev B | `main` | — | |

## 2. Current state

**What works (end of Phase 6):**
- The project builds (`./gradlew assembleDebug`) and unit tests pass (`./gradlew testDebugUnitTest`).
- Package / applicationId `com.matchitcampus.app`, minSdk 26, compile/target SDK 37.
- `MatchItApp` (Application) builds an `AppContainer` holding the app-wide `applicationScope`.
- Material 3 theme: dynamic colour on Android 12+, custom blue/teal/amber fallback palette,
  light and dark, system-bar icons synced through `SideEffect`.
- Navigation shell with type-safe routes (`ui/navigation/Routes.kt`):
  - Signed out: real Login and Register screens (Firebase Auth email/password, Phase 3).
  - Signed in: Home, Map, My posts, Claims, Profile tabs, plus Post item, Item detail
    (receives `itemId`), Pin picker, Debug tools and Zone Capture (debug builds only). No placeholders left.
  - Bottom `NavigationBar` under 600 dp width, side `NavigationRail` from 600 dp
    (`navigationSuiteTypeFor`, unit-tested).
- MediaPipe model `app/src/main/assets/universal_sentence_encoder.tflite` is committed and used by
  `ml/TextEmbedderHelper` (Phase 4).
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
- **Matching engine (Phase 4):**
  - `domain/matching/` is plain Kotlin (JVM-tested): `TextNormalizer`, `VectorMath` (cosine),
    `KeywordSimilarity` (Jaccard + query coverage), `MatchScorer` (hard filters + weighted score),
    `MatchWeights` (text 0.60, category 0.15, location 0.15, date 0.10, top 5) and `SearchScorer`
    (feed search ranking). `location/GeoUtils` has the haversine distance.
  - `ml/TextEmbedderHelper` is the only class that imports MediaPipe, behind the `Embedder`
    interface. It loads the model lazily, L2-normalises, doesn't quantise, runs on
    `Dispatchers.Default` and returns null if the model can't load (matching then uses keywords).
  - `data/repository/MatchRepository` is built in `AppContainer` (`by lazy`) and started in
    `MatchItApp.onCreate`. It watches Room for items with no embedding of the current model
    version (`ItemDao.observeNeedingEmbedding`), embeds them, then recomputes their matches
    against the open items of the opposite type and stores every pair **in both directions**.
    `observeMatches(itemId)` gives the best 5 live matches (Room Flow); `search(query, type)` ranks
    a feed tab. Logcat: `tag:MatchIt/MatchRepository` (per-component scores of the best matches).
  - The UI shows matches and search from Phase 5 on.
- Tests: 148 JVM unit tests after Phase 6 (it added `ZoneResolver`, `MapGeoJson`, `ItemPositions`,
  "Near me" sorting and zone JSON export tests; all green on 2026-11-24). Previously 126 JVM unit tests (Phase 5 added feed filtering, form validation, match explanations,
  photo sizing, formatters and My posts grouping) and 54 instrumented tests, including the
  real-model sanity test in `ml/TextEmbedderHelperTest` (both suites green on 2026-11-24, instrumented
  on AVD `Pixel_10_Pro`).
- **Core UI (Phase 5).** Every screen has a `XxxRoute` (ViewModel, events) and a stateless
  `XxxScreen`. ViewModels expose `UiState` as `StateFlow` and one-shot events as `SharedFlow`.
  - **Home** (`ui/home/`): Lost/Found tabs, semantic search (`debounce(300)` → `distinctUntilChanged`
    → `mapLatest`), filter chips (category, zone, last 7/30 days, status), pull-to-refresh
    (`SyncManager.syncNow`), loading/empty/error states. Tab and query are in `SavedStateHandle`.
  - **Post item** (`ui/post/`): create and edit (`Routes.PostItem(editItemId)`), Lost/Found
    segmented button, category dropdown, date picker (no future dates), zone bottom sheet,
    verification question (FOUND only), photo. Every field is in `SavedStateHandle` (checked with
    `am kill`). Post is disabled until valid; errors show under touched fields; a "Still needed" line
    lists what's missing. After posting it opens the new item.
  - **Item detail** (`ui/detail/`): photo, fields, status, top-5 matches with an animated score bar
    and an expandable "Why this match". Owner: Edit, Delete (confirm), Mark returned, claims with
    Accept (confirm) / Reject. Others: "This is mine" claim sheet (answer, message, link to one of my
    open lost posts, preselected from the matches), or my claim with Cancel. Recomputes the item's
    matches on open and downloads its photo if missing.
  - **Claims** (`ui/claims/`): "On my items" / "My claims" tabs with Accept/Reject/Cancel.
    `MainShell` shows a badge with pending incoming claims and a snackbar ("View") for
    `SyncManager.events`.
  - **My posts** (`ui/myposts/`): grouped by status under sticky headers, ⋮ menu with Edit,
    Mark returned, Delete.
  - **Profile**: About section (on-device matching, map attribution, version). Debug tools adds
    "Throw test exception" (`DebugTestException`).
  - **Photos**: `data/photo/PhotoStore` (Photo Picker → ≤ 800 px JPEG, ~150 KB target, 900 KB cap,
    files `filesDir/photos/{itemId}_{time}.jpg`), `PhotoRemoteDataSource` (`itemPhotos/{id}` Blob),
    `PhotoRepository` (upload queue in DataStore, download on open). `SyncManager.pushPending`
    uploads queued photos after their item is SYNCED; deleting an item also deletes its photo document.
- **Checked on the emulator against real Firebase (2026-11-24, AVD `Pixel_10_Pro`):** the full demo
  from the brief (Riya posts FOUND, Aman posts a differently worded LOST and sees it top at 94% with
  the breakdown, claims it, Riya gets the badge and accepts, both items RETURNED), semantic search
  reordering, editing, deleting, a photo uploaded by one account and downloaded on a fresh install by
  the other, and the Post draft surviving process death.
- **Location and map (Phase 6).**
  - `location/LocationClient`: one-shot fused fix (`getCurrentLocation`, high accuracy with fine
    permission, balanced with coarse only, `withTimeoutOrNull`, 10 s) and `locationUpdates()`
    (`callbackFlow`, removed in `awaitClose`).
  - `location/GeocoderClient`: reverse and forward geocoding, listener API on 33+, blocking call on
    `Dispatchers.IO` below; handles `Geocoder.isPresent() == false` and empty results.
  - `location/ZoneResolver.nearestZone`: the closest **non-placeholder** zone whose radius contains
    the point. `location/ItemPositions` (`mapPosition`, `distanceFrom`) and `location/MapGeoJson`
    (item points, zone polygons) are pure and unit-tested.
  - `ui/components/PermissionGate` (`LocationPermissionGate`): asks for fine + coarse together,
    rationale `AlertDialog`, "Open settings" after a permanent denial, approximate-only handled.
  - **Post item:** "Use my location" (fix → address line → suggested zone) and "Pick on map"
    (`Routes.PinPicker`: fixed centre pin, drag or tap, search a place; the result comes back
    through the previous entry's `SavedStateHandle`). Lat/lng/address are in the saved draft.
  - **Map tab** (`ui/map/`): OpenFreeMap liberty style centred on campus, Lost/Found dots in two
    colours (`CircleLayer` over a GeoJSON source), real zones as metre-accurate polygons, tap a dot
    for an `ItemCard` bottom sheet that opens the item, live "my location" started/stopped by a
    `DisposableEffect` + lifecycle observer, attribution always visible as text.
  - **Item detail:** a non-interactive mini map with one marker when the item has a position.
  - **Home "Near me"** filter chip: one fix, then `FeedLogic` sorts by haversine distance (pin, else
    real zone centre; unknown last). A search ranking still wins over distance.
  - **Zone Capture** (Debug tools): tap a zone → "Set to my current location" (precise only) →
    Room row updated with `isPlaceholder = false`; "Copy JSON" puts all zones, in the
    `campus_zones.json` format (with `_readme`), on the clipboard.

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
- **Phase 2, no `UserRepository` and no `MatchRepository` yet** (the `MatchRepository` part is
  **superseded** by Phase 4, which adds it). `UserDao` and `MatchDao` exist
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

- **Phase 4, MediaPipe `tasks-text` 1.2.3** (checked on Google Maven 2026-11-24), newer than the
  1.2.2 noted in Phase 0. The API matched the docs (`TextEmbedder.createFromOptions`,
  `setL2Normalize`, `setQuantize`).
- **Phase 4, no fixed score threshold (deviation from the brief's plan).** Measured on the real
  model, unrelated pairs of short descriptions scored 0.56–0.90 cosine (avg 0.73) and true pairs
  0.89–0.99, so no cut-off separates them. The ranking was right for all 10 test items, so each
  item shows its best `MatchWeights.maxResults` (5) instead. `SearchScorer` likewise returns every
  item, ordered, with a +0.3 bonus for an exact substring hit.
- **Phase 4, hard filters in `MatchScorer`:** opposite types, both OPEN, different owners, found no
  more than 1 day before lost (grace for guessed dates), at most 30 days apart. Location score is 1
  in the same zone, otherwise falls to 0 over 1 km, 0.5 if unknown. Date score falls to 0 at 14 days.
  Category: 1 same, 0.3 if either is OTHER, else 0. Every component is symmetric.
- **Phase 4, embeddings stay local** (never in Firestore) and carry `embeddingVersion`
  (`TextEmbedderHelper.MODEL_VERSION`). A vector from another version counts as missing, so
  replacing the model only needs the constant raised. `updateEmbedding` only writes if the row's
  `updatedAt` is unchanged, so an edit during embedding isn't overwritten with a stale vector.
- **Phase 4, `MatchRepository` observes Room instead of being called** by `ItemRepository` or
  `SyncManager`, which keeps them free of a dependency on it (no cycle). This replaces the Phase 3
  note's idea of calling `ensureEmbeddings()` after `applyItemPage` / local saves.
- **Phase 4, all passing pairs are cached, not just the top 5,** because a pair can be in one
  item's top 5 but not the other's. `observeLiveMatches` joins `items` so claimed/returned/deleted
  items drop out without recomputing, and works out `usedFallback` in SQL, so the v1 `matches`
  table needed no new column (DB stays v1, no migration).

- **Phase 5, Coil 3.6.3** (re-checked on Maven Central 2026-11-24, unchanged since Phase 0). Only
  `coil-compose`: photos are local files, so no network module.
- **Phase 5, ViewModels for Home and Post depend on small interfaces** (`FeedDataSource`,
  `PostItemDataSource`) with real implementations that forward to the repositories, so Phase 8 can
  unit-test them with fakes. Other ViewModels take the repositories directly.
- **Phase 5, text fields read straight from a `StateFlow` that the ViewModel updates
  synchronously** (`HomeViewModel.query`, `PostItemViewModel.form`, `ItemDetailViewModel.claimDraft`),
  not through the `combine`d `uiState`. Going through `combine` adds an async step, which can drop or
  misplace letters when typing fast.
- **Phase 5, location on the Post screen is the zone only** (bottom sheet). `latitude`/`longitude`
  stay null until Phase 6 adds "Use my location" and the pin picker, so matching uses the zone
  rules (same zone = 1, otherwise 0.5 unknown). A zone is required; "Other / Not sure" exists for
  people who don't know.
- **Phase 5, `eventDate` is midnight UTC of the chosen day** (what the M3 date picker returns) and is
  always read back with UTC (`ui/util/Formatters`). "Today" is the phone's calendar day.
- **Phase 5, photo files are named `{itemId}_{time}.jpg`** instead of the brief's `{itemId}.jpg`.
  Coil 3 caches file images by path only, so a replaced photo with the same name would keep
  showing the old picture; a new name per version avoids that. Old files of the item are deleted.
- **Phase 5, the photo upload queue is a set of item ids in DataStore** (`SyncPreferences`), not a
  Room column, so the schema stays v1 and a status change doesn't re-upload the photo.
  `SyncManager` uploads a queued photo only once its item is SYNCED (the rules need the item first).
- **Phase 5, Post form rules:** title 3–60 chars, description required (≤ 500), category and zone
  required, no future dates, verification question required for FOUND (≤ 120).
- **Phase 5, claims:** one live claim per person per item (the button hides while my claim is
  PENDING or ACCEPTED; a rejected or cancelled claimant may claim again). Accepting from the item
  screen or the Claims tab asks for confirmation.
- **Phase 5, fixed a race in the Phase 4 `MatchRepositoryTest`:** its `tearDown` cancelled the
  watcher's scope and closed the database straight away, so a query still running crashed the
  test process on `Pixel_10_Pro` (only 41 of 54 tests ran). It now uses `cancelAndJoin()` first.
- **Phase 5, Item detail's mini map is left for Phase 6** (MapLibre is added there); the screen shows
  the zone name and address line for now. **Superseded** by Phase 6 (mini map added).

- **Phase 6, Play Services Location 21.4.0 and MapLibre Compose 0.20.0** (both re-checked
  2026-11-24, unchanged since Phase 0). Map content uses MapLibre Compose's GeoJSON sources with
  `CircleLayer` / `FillLayer` / `LineLayer` composables rather than marker composables.
- **Phase 6, zones are drawn as 48-point polygons** of their real radius in metres, because a
  `CircleLayer` radius is in screen pixels and wouldn't scale with zoom.
- **Phase 6, placeholder zones are hidden on the maps and never used for positions.** They all sit
  at the made-up centre, so an item with only a placeholder zone and no pin isn't drawn (the Map tab
  shows a count of such items) and sorts last in "Near me".
- **Phase 6, a suggested zone replaces the chosen one only if a real zone contains the spot**;
  otherwise the user's zone is kept. While every zone is a placeholder, nothing is suggested.
- **Phase 6, the pin picker returns its result through `SavedStateHandle`** of the previous back
  stack entry (a `DoubleArray`), which the Post screen consumes once and clears.
- **Phase 6, "Near me" uses one location fix** when the chip is turned on, not live updates, so the
  list doesn't jump while scrolling.
- **Phase 6, Zone Capture refuses approximate location** (a zone centre must be precise) and only
  changes Room. Captured zones reach other phones only by pasting the exported JSON into
  `campus_zones.json` and reinstalling (seeding only runs on an empty table, see Phase 2).
- **Phase 6, attribution is plain text** on every map (`map_attribution` string), not MapLibre's
  "i" button, so it's always visible.

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
| MediaPipe tasks-text | 1.2.3 (re-checked 2026-11-24) | in use (Phase 4) |
| Play Services Location | 21.4.0 (re-checked 2026-11-24) | in use (Phase 6) |
| MapLibre Compose | 0.20.0 (minSdk 24, re-checked 2026-11-24) | in use (Phase 6) |
| Coil 3 (`io.coil-kt.coil3:coil-compose`) | 3.6.3 (re-checked 2026-11-24) | in use (Phase 5) |
| Turbine | 1.2.1 | in use (androidTest) |
| JUnit 4 / AndroidX test JUnit / Espresso | 4.13.2 / 1.3.0 / 3.7.0 | in use |
| AndroidX test core / runner | 1.7.0 / 1.7.0 | in use (androidTest) |

## 4. Known issues / TODOs

- The merged branch `feature/01-scaffold` still exists on GitHub. It's safe to delete it
  (GitHub's "Delete branch" button on PR #1).
- No tablet emulator exists on Dev A's laptop yet. Create one before Phase 7 (two-pane layout).
- The launcher icon is still the Android Studio default.
- `ZoneSeedParserTest.realSeedFile_isStillAllPlaceholders` must be updated once real zone
  coordinates are captured. The Zone Capture tool exists since Phase 6; walking the campus and
  pasting the exported JSON into `campus_zones.json` is still to do (Dev A/Dev B by hand).
- **Phase 6 wasn't tried on an emulator or device in this session's checkpoint** (only build + JVM
  tests). Check the Map tab, "Use my location", the pin picker and Zone Capture at the start of
  Phase 7. On an emulator, set a location under Extended controls → Location first.
- The maps need the network for tiles (OpenFreeMap); offline they show the grey background only.
- ~~Deleting an item that was never uploaded still waits for sync.~~ Resolved in Phase 3 (see decisions).
- ~~`SyncManager.events` is emitted but not shown yet.~~ Resolved in Phase 5 (snackbar in `MainShell`).
- ~~Photos aren't uploaded yet.~~ Resolved in Phase 5 (see decisions).
- Last-write-wins uses phone clocks, so two phones with very different clocks can pick the wrong
  winner. It's acceptable because each document normally has a single writer (its owner).
- If someone logs out with unsynced changes and another account logs in on the same phone, the
  old rows stay `PENDING_UPLOAD` (they're skipped, because the rules would reject them).
- Matches aren't recomputed automatically when a remote change keeps the text (e.g. a moved pin or
  a new date). Partly resolved in Phase 5: the item screen calls `recomputeMatchesFor(itemId)` when it
  opens, so what you look at is always fresh.
- `MatchWeights` are reasoned starting values, not tuned. Revisit with the Phase 7 demo data.
- **Photos, Phase 5 limitations:** if an owner *replaces* a photo, other phones that already
  downloaded the old one keep showing it (they only download when they have no copy). Removing a
  photo while editing leaves the old `itemPhotos` document in Firestore (harmless, just orphaned).
  On Android 8 (API 26–27) photos aren't rotated by their EXIF tag, so a few may appear sideways.
- **Phase 8 ViewModel tests:** ViewModels log with `android.util.Log`, which throws in plain JVM
  tests. Add `testOptions { unitTests.isReturnDefaultValues = true }` to `app/build.gradle.kts`
  (or avoid the logging paths) when writing the `HomeViewModel` / `PostItemViewModel` tests.
- The debug APK is about 169 MB, mostly MediaPipe's native libraries for every CPU architecture.
  Phase 7 could add ABI splits or `ndk { abiFilters }` for release builds.
- The bottom navigation bar stays visible on the Post and Item screens (the Phase 1 shell puts the
  whole NavHost inside it). Fine for now; Phase 7 can hide it on non-tab screens if wanted.
- The real-model sanity test needs a device/emulator (`./gradlew connectedDebugAndroidTest`),
  because MediaPipe runs native code. The JVM tests use a fake `Embedder`.

## 5. Per-laptop setup notes

Things each laptop needs that are **not** in the repo:
- `MATCHIT_CAMPUS_PROMPT.md` in the project root (shared directly, git-ignored).
- `app/google-services.json` from the Firebase console (project `matchit-campus`, Android app
  `com.matchitcampus.app`). Git-ignored. Without it the build fails at `processDebugGoogleServices`.
- JDK 17+ (Android Studio's bundled JDK is fine) and Android SDK platform 37.
- Dev B's laptop: Android Studio 2025.3 (build 253), hence AGP 9.2.0. AVD `Pixel_9_Pro` is used
  for the connected tests.
- The `.claude/` folder in the project root (shared directly, git-ignored).
- **Dev A's emulator (`Pixel_10_Pro`):** start it with `-no-snapshot-load` (its saved snapshot came
  from the disk-full crash and leaves adb "offline"). If the app says it can't reach the server,
  check Wi-Fi in the emulator (it was found switched off once). Its `/data` is nearly full
  (~700 MB free): if `adb install` fails with INSUFFICIENT_STORAGE, run `adb shell pm trim-caches 4G`
  or wipe the AVD's data in Device Manager.
- **Dev A's network has Sophos SSL inspection.** Now and then it replaces Google's certificate with
  its own, which Java (Gradle) and Node don't trust, so downloads fail with `PKIX path building
  failed`. Retrying works because Gradle caches what it already downloaded. A permanent fix is to
  import the Sophos CA into the JDK truststore, or to use another network.

## 6. Next phase

**Phase 7: Polish + migration**, driven by **Dev A**, working directly on `main`.
- `git checkout main && git pull` first. Phase 6 is on `main`.
- First, try Phase 6 on the emulator (see Known issues).
- Migration v2: add nullable `color` to `items` with an explicit `Migration(1, 2)` and a
  `MigrationTestHelper` test. The rules already allow `color` (Phase 3). Add it to the Post form,
  `TextNormalizer.forEmbedding`, the Firestore mappers and the detail screen. Adding colour changes
  the embedded text, so existing rows need re-embedding (raising `TextEmbedderHelper.MODEL_VERSION`
  is the simplest way to trigger it).
- Two-pane Home on expanded width (needs a tablet emulator on Dev A's laptop), animations and a
  performance pass, and the debug "Seed demo data" (items can now carry pins: give them real
  coordinates near campus so they show on the map).
- Optional from the Known issues: hide the bottom bar on non-tab screens, ABI splits for release.
