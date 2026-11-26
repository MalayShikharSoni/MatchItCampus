# Learning notes

One section per phase: the concepts used, the files to read first, and questions an
examiner might ask, with short answers.

---

## Phase 1: Project scaffold

### Concepts used

- **Gradle version catalog** (`gradle/libs.versions.toml`): all library and plugin versions in
  one file. Build scripts refer to them as `libs.xxx` / `libs.plugins.xxx`, so a version is
  changed in one place.
- **AGP 9 built-in Kotlin**: the Android Gradle Plugin compiles Kotlin itself, so there is
  no separate `kotlin-android` plugin. The Kotlin JVM target follows `compileOptions`.
- **KSP vs kapt**: KSP reads Kotlin code directly, so it's faster than kapt, which first
  generates Java stubs. Room uses it from Phase 2.
- **Application class + manual DI**: `MatchItApp` creates one `AppContainer` that owns
  app-wide objects. There is no DI framework, just constructors.
- **Application-level CoroutineScope**: `SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler`.
- **Material 3 theming**: dynamic colour (Android 12+), a fallback colour scheme, light/dark.
- **`SideEffect`**: runs after every successful recomposition. Used to keep the status/navigation
  bar icon colours in sync with the theme.
- **Type-safe Navigation Compose**: `@Serializable` objects/classes as routes, `composable<T>`,
  `toRoute<T>()`.
- **Adaptive layout**: `NavigationSuiteScaffold` + `WindowSizeClass` choose a bottom bar or a
  side rail by window width.
- **Stateless, slot-based composables**: `PlaceholderScreen` takes a title, optional callbacks
  and content slots, and holds no state of its own. Every screen has a `@Preview`.

### Files to read first

1. `app/build.gradle.kts` and `gradle/libs.versions.toml`
2. `MatchItApp.kt` → `di/AppContainer.kt`
3. `MainActivity.kt` → `ui/navigation/MatchItRoot.kt`
4. `ui/navigation/Routes.kt`, `MainShell.kt`, `MainNavHost.kt`, `TopLevelDestination.kt`
5. `ui/theme/Theme.kt`
6. `ui/components/PlaceholderScreen.kt`
7. Test: `app/src/test/.../ui/navigation/NavigationSuiteTypeTest.kt`

### Likely viva questions

1. **Why is there no Hilt or Koin?**
   We use manual DI: `AppContainer` builds dependencies with plain constructors. It is
   small enough to read top to bottom, has no generated code, and shows what a DI
   framework would automate.

2. **Why does `AppContainer` keep `context.applicationContext` and not the context passed in?**
   The container lives as long as the app. Holding an Activity context would keep that
   Activity in memory after it's destroyed, which is a memory leak. The application context
   lives as long as the process, so it's safe.

3. **What does `SupervisorJob` change in `applicationScope`?**
   With a normal `Job`, one failing child cancels the parent and all its siblings. With
   `SupervisorJob`, a failure stays in that child, so for example one failed sync attempt
   doesn't stop other background work.

4. **What is a `CoroutineExceptionHandler` for?**
   It catches exceptions that nothing else handled in coroutines started with `launch` in that
   scope. We log them with `Log.e` instead of crashing. It doesn't replace try/catch where an
   error should be shown to the user.

5. **Why `SideEffect` for the system bars and not `LaunchedEffect`?**
   We need to push Compose state (`darkTheme`) to a non-Compose object (the window) after
   *every* successful recomposition, with no coroutine and no key. That is exactly what
   `SideEffect` does. `LaunchedEffect` is for suspend work tied to a key.

6. **What are type-safe routes, and what do they give us over string routes?**
   Routes are `@Serializable` Kotlin types. `navigate(Routes.ItemDetail(itemId))` can't
   be called without the argument, and `toRoute<Routes.ItemDetail>()` returns a typed
   object. With string routes like `"detail/{id}"` you can typo the string or read the
   argument with the wrong type.

7. **How does the app switch between a bottom bar and a side rail?**
   `currentWindowAdaptiveInfoV2()` gives the current `WindowSizeClass`. `navigationSuiteTypeFor`
   returns `NavigationBar` under 600 dp of width and `NavigationRail` from 600 dp, and
   `NavigationSuiteScaffold` draws whichever one it's given. Rotation recomposes with the
   new size, so the layout switches automatically. The rule is unit-tested.

8. **Why do screens get lambdas like `onBack` instead of the `NavController`?**
   It keeps screens stateless and independent of navigation, so they can be previewed and
   tested with dummy lambdas. Only `MainNavHost` knows how an event becomes a navigation call.
   This is state hoisting applied to events.

---

## Phase 2: Room data layer

### Concepts used

- **Room architecture**: `@Entity` classes are tables, `@Dao` interfaces declare queries,
  and the `@Database` class ties them together. KSP generates the code that runs the SQL.
  Room checks every `@Query` against the schema at **compile time**, so a typo in a column
  name fails the build, not the app.
- **Five tables**: `items`, `campus_zones`, `claims`, `users` (cached profile) and `matches`
  (cached match scores, with a composite primary key `(sourceItemId, candidateItemId)`).
- **Indices** on the columns the app filters or sorts by (`type`, `status`, `zoneId`, `ownerId`,
  `updatedAt` on items). No foreign keys, because synced rows can arrive in any order.
- **Relationships**: `ItemWithZone` and `ClaimWithItems` use `@Embedded` + `@Relation`. Room runs
  one extra query per relation, so those DAO methods are `@Transaction`.
- **TypeConverters**: enums are stored by name (not ordinal). An embedding `FloatArray` is
  stored as a little-endian BLOB (4 bytes per float).
- **Room with Flow**: observed queries return `Flow`. Room re-runs the query and emits again
  whenever a table it reads changes, so the UI updates itself after any write.
  One-off reads and writes are `suspend` functions.
- **`@Upsert`**: insert, or update the row with the same primary key.
- **Transactions**: `database.withTransaction { }` (room-ktx) for changes that span two
  tables (accepting a claim). `@Transaction` on a DAO method with a body (`MatchDao.replaceMatchesFor`).
- **Exported schema**: the Room Gradle plugin writes `app/schemas/.../1.json`. It's committed,
  and the Phase 7 migration test uses it.
- **Offline-first write rules**: writes go to Room marked `PENDING_UPLOAD` / `PENDING_DELETE`.
  Deleted rows stay hidden until sync (Phase 3) deletes them remotely.
- **Repository pattern + domain models**: repositories map entities to domain `Item`/`Claim`/
  `CampusZone`, so screens never see Room types.
- **First-run seeding**: `ZoneRepository.seedIfEmpty()` uses a `count() == 0` check, launched
  in `applicationScope` from `MatchItApp.onCreate`. The JSON is parsed with kotlinx.serialization.
- **Testable design**: a `clock: () -> Long` and a `readSeedJson: () -> String` are passed in,
  so tests control the time and the file contents.
- **Testing**: JVM tests for converters, mappers and the seed file. Instrumented tests on an
  in-memory database (`Room.inMemoryDatabaseBuilder`), with `runTest` and Turbine for Flow emissions.

### Files to read first

1. `domain/model/Enums.kt`, `Item.kt`, `Claim.kt`, `CampusZone.kt`
2. `data/local/entity/ItemEntity.kt` (then the other entities)
3. `data/local/Converters.kt`
4. `data/local/dao/ItemDao.kt`, `ClaimDao.kt`, `MatchDao.kt`
5. `data/local/relation/ItemWithZone.kt`, `ClaimWithItems.kt`
6. `data/local/AppDatabase.kt` and `app/schemas/.../1.json`
7. `data/repository/ItemRepository.kt`, `ClaimRepository.kt`, `ZoneRepository.kt`
8. `di/AppContainer.kt`, `MatchItApp.kt`
9. Tests: `app/src/test/.../ConvertersTest.kt`, `app/src/androidTest/.../ItemDaoTest.kt`,
   `ClaimRepositoryTest.kt`

### Likely viva questions

1. **Why store the embedding as a BLOB instead of JSON?**
   A BLOB is exactly 4 bytes per float and is converted with one memory copy. JSON text is
   about three times bigger and must be formatted and parsed digit by digit, which matters
   because matching reads every candidate's embedding. A binary copy also loses no precision.

2. **Why are enums stored by name rather than by ordinal?**
   Ordinals change if someone adds or reorders a constant, which would silently change the
   meaning of every stored row. Names stay correct unless a constant is renamed, and they're
   readable in App Inspection.

3. **How does a `Flow` from a DAO know when to emit again?**
   Room tracks which tables each query reads. When any write through the same database
   instance changes one of those tables, Room re-runs the query and emits the new result.
   That's why there must be only one `AppDatabase` instance.

4. **What does `@Transaction` do on a method returning `ItemWithZone`?**
   `@Relation` makes Room run two queries (items, then their zones). `@Transaction` runs them
   in one transaction, so a write in between can't give an item a zone that doesn't match.

5. **Why does accepting a claim use `withTransaction`?**
   It changes three things: the claim (ACCEPTED), the item (RETURNED), and the other pending
   claims (REJECTED). In a transaction they all happen or none do, and observers never see a
   half-finished state.

6. **Why does deleting an item only mark it `PENDING_DELETE`?**
   If we removed the row straight away while offline, Phase 3's sync would never know to
   delete the Firestore document, and the next download would bring the item back. The row
   stays hidden until the remote delete succeeds.

7. **Why seed zones with a count check instead of `RoomDatabase.Callback.onCreate`?**
   The callback only gets a raw SQLite connection (no DAOs), and it runs only once: if seeding
   failed then, it would never retry. The count check retries on the next launch and never
   overwrites zones already corrected with Zone Capture.

8. **What is `exportSchema` for?**
   Room writes each database version's schema as JSON in `app/schemas/`. It gives a history
   of schema changes in Git, and `MigrationTestHelper` uses those files to create a v1
   database and check that the migration to v2 works (Phase 7).

---

## Phase 3: Firebase Auth, Firestore and sync

### Concepts used

- **Firebase Auth (email + password)** wrapped in `AuthRepository`. The sign-in state is a
  `StateFlow<AuthUser?>` built from an `AuthStateListener` with `callbackFlow` + `stateIn`.
- **`callbackFlow` + `awaitClose`**: turns callback APIs (auth listener, Firestore snapshot
  listeners, the network callback) into Flows. The callback is removed when collection stops.
- **Cloud Firestore** with a memory-only cache, so Room stays the only thing on disk.
- **Offline-first sync**: Room is the single source of truth. Local writes are marked
  `PENDING_UPLOAD` / `PENDING_DELETE` and pushed later; remote changes are merged into Room.
- **Last-write-wins conflict resolution** in a pure function (`SyncMerge`).
- **Incremental sync with server timestamps** (quota awareness on the Spark plan).
- **Soft delete (tombstones)**, so deletions reach other phones.
- **Flow operators**: `combine`, `map`, `distinctUntilChanged`, `debounce`, `filter`,
  `flatMapLatest`, `collectLatest`, `first`, `onEach`, `stateIn`.
- **Structured concurrency**: one sync session = one `coroutineScope` with three children,
  cancelled together. `SupervisorJob` and `CoroutineExceptionHandler` on the app scope.
- **Timeouts and cancellation**: `withTimeoutOrNull` around network calls;
  `CancellationException` is always re-thrown.
- **Lifecycle-aware sync**: `ProcessLifecycleOwner` (whole-app foreground state).
- **DataStore Preferences** for the last sync time.
- **External scope pattern**: `externalScope.async { … }.await()` for work that must finish
  even if the screen that started it is destroyed.
- **Security rules**: authentication, ownership, field validation, and allowed status changes.
- **MVVM with `UiState`**: Login/Register/Profile ViewModels expose one immutable state object
  as `StateFlow`; Debug tools uses a `SharedFlow` for one-shot snackbar messages, collected in
  `LaunchedEffect`.

### Files to read first

1. `data/repository/AuthRepository.kt`
2. `data/sync/SyncManager.kt`, then `SyncMerge.kt` and `ConnectivityObserver.kt`
3. `data/remote/ItemRemoteDataSource.kt` (the incremental query) and `ClaimRemoteDataSource.kt`
4. `data/remote/mapper/RemoteMappers.kt` and `data/remote/dto/RemoteDtos.kt`
5. `firebase/firestore.rules`
6. `ui/auth/LoginViewModel.kt` + `LoginScreen.kt` (Route/Screen split), `ui/navigation/RootViewModel.kt`
7. `di/AppContainer.kt` (how Firestore is configured and everything is wired)
8. Tests: `SyncMergeTest`, `RemoteMappersTest`, `AuthValidatorTest`

### Likely viva questions

1. **What does "Room is the single source of truth" mean in your sync design?**
   Screens only ever observe Room. Firestore data reaches the UI only by being written into
   Room by `SyncManager`. So the UI works the same online and offline, and there's one place
   that decides what the current data is.

2. **How does a new post get to Firestore?**
   The repository writes it to Room as `PENDING_UPLOAD`. `SyncManager` observes the count of
   unsynced rows together with network state (`combine` + `debounce`). When the count is above
   zero and we're online, it uploads the row and then marks it `SYNCED`, but only if the row
   didn't change during the upload (`markSynced(id, updatedAt)`).

3. **How do you avoid using up the free Firestore quota?**
   We never re-read whole collections. The item listener asks only for documents whose server
   `updatedAt` is after the last one we saw (stored in DataStore), at most 200 at a time. Claims
   are queried only for the signed-in user. Listeners run only while signed in and in the
   foreground. Firestore's own disk cache is off, so nothing is stored twice.

4. **Why two timestamps, `updatedAt` and `updatedAtMillis`?**
   `updatedAt` is set by the server (`FieldValue.serverTimestamp()`, enforced in the rules), so
   "changed since" queries can't be broken by a phone with a wrong clock. `updatedAtMillis` is the
   phone's time of the change, which is what Room stores, so last-write-wins compares like with like.

5. **Why don't you delete Firestore documents?**
   Other phones only ask for "documents changed since X". A deleted document never shows up in
   that query, so they'd keep the item forever. Writing `deleted: true` is a change they do see,
   and they remove their local copy.

6. **What happens when the user logs out?**
   Firebase signs out, `authState` becomes null, and `SyncManager`'s `collectLatest` cancels the
   session, which removes the listeners. We wait for that null, then clear claims and the cached
   profile; public items stay. If there are unsynced changes, Profile first shows an `AlertDialog`.

7. **Why does sign-in run in `applicationScope` and not `viewModelScope`?**
   As soon as sign-in succeeds, the root swaps the login screen for the main app. That clears
   the login ViewModel and cancels `viewModelScope`. The profile save after sign-in would be cut
   off. `externalScope.async { }.await()` runs it in the app-wide scope, so it always finishes.

8. **How do the security rules stop a claimant from marking the finder's item as claimed?**
   Item updates are allowed only for `resource.data.ownerId == request.auth.uid`. The claimant
   can only create a claim (as themselves, PENDING, on someone else's FOUND item). The finder's own
   phone sees the claim through its listener and changes the item status itself.

## Phase 4: Matching engine

### Concepts used

- **Text embeddings**: the Universal Sentence Encoder (bundled `.tflite`, run by MediaPipe's
  Text Embedder) turns a sentence into 100 numbers that capture its meaning. Similar meanings
  give vectors pointing in similar directions, even with no shared words.
- **Cosine similarity**, written in plain Kotlin (`VectorMath`) so it's unit-tested.
- **Keyword fallback**: token Jaccard similarity after normalising and removing stop words,
  used when an item has no embedding (flagged `usedFallback`).
- **A pure, explainable scorer** (`MatchScorer`): hard filters, then four component scores
  (text, category, location, date) combined with named weights (`MatchWeights`).
- **Measuring before trusting**: on the real model, unrelated pairs also score 0.56–0.90, so
  a fixed threshold can't work. The ranking was right 10 out of 10 times, so the app shows the
  **top 5** by score instead of filtering by a threshold.
- **Haversine distance** (`GeoUtils`) for "how far apart".
- **Dispatchers**: model inference and scoring on `Dispatchers.Default` (CPU work), Room on its
  own IO threads. A `Mutex` serialises access to the embedder and to embedding passes.
- **A Room Flow as a trigger**: `MatchRepository.start()` observes "items that need an
  embedding" (`map` + `distinctUntilChanged` + `filter`) and reacts by itself, so the item
  repository and sync don't need to know matching exists (no dependency cycle).
- **Room techniques**: a guarded `UPDATE … WHERE updatedAt = :updatedAt` (don't store a vector
  for text that has since changed), a JOIN query returning a POJO with `@Embedded` plus a
  computed column, `LIMIT`, and a `@Transaction` DAO method that deletes and re-inserts.
- **Interface for testability**: `Embedder` lets tests use a fake instead of the real model.

### Files to read first

1. `domain/matching/MatchScorer.kt` and `MatchWeights.kt` (the rules and why)
2. `domain/matching/TextNormalizer.kt`, `KeywordSimilarity.kt`, `VectorMath.kt`
3. `ml/TextEmbedderHelper.kt` (the only MediaPipe class)
4. `data/repository/MatchRepository.kt` (embedding lifecycle, storing both directions)
5. `data/local/dao/MatchDao.kt` (`observeLiveMatches`) and the new queries in `ItemDao.kt`
6. `domain/matching/SearchScorer.kt` (search ranking, used by the feed in Phase 5)
7. Tests: `MatchScorerTest` (JVM), `MatchRepositoryTest` and `TextEmbedderHelperTest` (device)

### Likely viva questions

1. **How can "black earphone case" match "JBL earbuds cover" with no words in common?**
   Each text is turned into an embedding by the on-device model. The model was trained so that
   sentences with similar meaning get vectors pointing the same way. We compare the vectors
   with cosine similarity, which doesn't care about exact words.

2. **Why are embeddings not stored in Firestore?**
   Every phone computes them locally from the same bundled model, so they always match the
   model in use. It keeps Firestore documents small (fewer bytes on the free plan), and if the
   model changes, raising `MODEL_VERSION` makes phones recompute instead of mixing vectors from
   two models.

3. **What happens if the model fails to load?**
   `TextEmbedderHelper` logs the error once and returns null. The scorer then uses keyword
   Jaccard similarity instead, and the result is marked `usedFallback`. The app keeps working,
   just with rougher text matching.

4. **Why did you drop the score threshold?**
   We measured it on the device. This model gives every pair of short descriptions a high
   cosine value: true pairs 0.89–0.99, unrelated pairs 0.56–0.90. A fixed cut-off would let
   unrelated items through, or drop real matches. But for each of 10 lost posts the true found
   post was ranked first, so we rely on the order and show the top 5.

5. **Why is every pair stored, and in both directions?**
   The score is symmetric, so one computation serves both owners. Because each item shows its
   own top 5, a new lost item can be in a found item's top 5 even when it isn't the other way
   round. Storing every pair that passes the hard filters keeps both lists correct. The filters
   (opposite type, open, different owner, within 30 days) keep the table small.

6. **When are matches recomputed?**
   When an item gets a new embedding: after it's created or edited here (saving clears the
   embedding) or when it arrives from sync without one. `MatchRepository.start()` watches Room
   for such items. When an item stops being OPEN, the `observeLiveMatches` JOIN hides it at once,
   with no recompute.

7. **Why write cosine similarity yourself instead of using MediaPipe's?**
   So the domain code has no Android dependency and runs in JVM unit tests. It's a few lines,
   and the tests cover identical, orthogonal, opposite and zero vectors.

8. **Why does the embedding update check `updatedAt`?**
   Embedding takes time. If the user edits the item meanwhile, the vector belongs to the old
   text. The `WHERE updatedAt = :updatedAt` makes the update do nothing in that case, and the
   edited row is embedded again on the next pass.

## Phase 5: Core UI

### Concepts used

- **MVVM with unidirectional data flow**: every screen has a `XxxRoute` (collects state, owns
  the ViewModel, handles one-shot events) and a stateless `XxxScreen(state, callbacks)` that only
  draws. Events go up as lambdas (or one `onAction(PostItemAction)`), state comes down.
- **`UiState` data classes as `StateFlow`**, built with `combine(...)` and `stateIn(viewModelScope,
  WhileSubscribed(5_000), initial)`. The 5 s keeps the flows alive across a rotation but stops
  them when the user really leaves.
- **One-shot events as `SharedFlow`** (snackbars, "navigate after saving"), collected in a
  `LaunchedEffect(Unit)`. `rememberUpdatedState` keeps the long-lived collector calling the
  newest `onSaved` / `onBack` lambda.
- **Flow operators for search**: `debounce(300)` → `distinctUntilChanged()` → `mapLatest { }`
  (an old search is cancelled when new text arrives). `flatMapLatest` switches the feed when the
  tab or the signed-in user changes.
- **`SavedStateHandle`**: the feed's tab and query, and every field of the Post draft, survive
  process death. Navigation arguments arrive in the same handle (`toRoute<Routes.ItemDetail>()`,
  or the `editItemId` key).
- **Text fields read their value straight from a `StateFlow` the ViewModel updates
  synchronously** (search text, Post form, claim sheet), never through `combine`, which adds an
  async step and drops letters when typing fast.
- **Interfaces for testable ViewModels**: `FeedDataSource` and `PostItemDataSource` list only what
  the screen needs; the real versions forward to the repositories; tests can pass fakes.
- **Material 3**: `PrimaryTabRow`, `FilterChip` + `DropdownMenu`, `SegmentedButton`,
  `ExposedDropdownMenuBox`, `DatePickerDialog` with `SelectableDates`, `ModalBottomSheet`
  (zone picker, claim sheet), `AlertDialog` confirmations, `PullToRefreshBox`, `BadgedBox`,
  `ExtendedFloatingActionButton` that collapses on scroll.
- **Compose performance**: `key` + `contentType` in lazy lists, `derivedStateOf` for the FAB,
  `@Immutable` UI state, `remember` for the detail callbacks object.
- **Animations with a purpose**: `Crossfade` between loading/empty/error/list,
  `AnimatedVisibility` for the verification question and the "why this match" lines,
  `animateFloatAsState` for the score bar, `animateContentSize` on item cards.
- **Photos**: system Photo Picker (`PickVisualMedia`, no permission), `ImageDecoder` /
  `BitmapFactory` + `inSampleSize` to shrink to ≤ 800 px, JPEG quality steps to ~150 KB,
  stored as a Firestore Blob in `itemPhotos/{id}`, uploaded after the item, downloaded on demand,
  shown with Coil's `AsyncImage`.
- **Accessibility**: a transparent click overlay hides a field from TalkBack, so the overlay gets
  `Role.Button` and a `contentDescription` with label and value; radio options use
  `selectableGroup` + `selectable(role = RadioButton)`.

### Files to read first

1. `ui/home/HomeViewModel.kt` (the search Flow chain, `combine`, SavedStateHandle) and `FeedFilters.kt`
2. `ui/post/PostItemViewModel.kt` (draft in SavedStateHandle, validation, save + photo) and `PostItemForm.kt`
3. `ui/detail/ItemDetailViewModel.kt` (matches joined with items, who-can-do-what, the claim) and `MatchExplanation.kt`
4. `ui/claims/ClaimsViewModel.kt`, `ui/navigation/ShellViewModel.kt` + `MainShell.kt` (badge, snackbar)
5. `data/photo/PhotoStore.kt`, `data/repository/PhotoRepository.kt`, the photo loop in `SyncManager.pushPending`
6. `ui/components/` (ItemCard, StatusChip, MatchScoreBar, ZonePickerSheet, ClaimCard, StateViews)
7. Tests: `FeedLogicTest`, `PostItemValidatorTest`, `MatchExplanationTest`, `PhotoSizingTest`

### Likely viva questions

1. **Why split each screen into a Route and a Screen?**
   The Screen is stateless: it takes plain data and lambdas, so it can be previewed and
   UI-tested with fake state. Only the Route knows about the ViewModel, navigation and
   Activity-level things like the Photo Picker launcher. That is state hoisting.

2. **Walk through what happens when I type "earphones" in the search box.**
   Each keystroke is saved to `SavedStateHandle`, so the box updates at once. `debounce(300)`
   waits for a 300 ms pause, `distinctUntilChanged` skips repeats, and `mapLatest` embeds the
   query and ranks the tab's items (cancelling an older search still running). `combine` merges
   the ranking with the feed and filters into a new `HomeUiState`, and Compose redraws the list.

3. **How does the Post draft survive the app being killed?**
   Every change is copied into `SavedStateHandle`, which Android stores with the Activity's
   saved state. After process death the ViewModel is recreated and `restoreForm()` reads the
   fields back. The picked photo is already a compressed file in `filesDir`, and only its path is
   saved. We tested this with `adb shell am kill`.

4. **Why are snackbars and "navigate after save" a SharedFlow and not part of UiState?**
   They should happen once. If they were state, rotating the screen would show the snackbar
   again or navigate twice. A SharedFlow with no replay delivers each event to the collector
   that is active at that moment.

5. **How do photos work without Cloud Storage?**
   The picked image is shrunk to 800 px and compressed to about 150 KB, then stored as bytes
   (a Blob) in a Firestore document, `itemPhotos/{itemId}`, separate from `items` so the feed
   never downloads images. The rules allow writes only by the item's owner and only once the item
   exists, so sync uploads the item first. Others download a photo only when they open the item.

6. **Why is the photo upload queue in DataStore and not a column?**
   Adding a column would change the database schema (a migration). The queue is just a small
   set of item ids, it must survive restarts, and it shouldn't re-upload the photo on every status
   change. DataStore fits that, and SyncManager watches its size to trigger a push.

7. **Who changes what when a claim is accepted?**
   The finder's phone, in one Room transaction: claim ACCEPTED, found item RETURNED, other
   pending claims REJECTED. Sync pushes those (the finder owns them). The claimant's phone sees
   the accepted claim through its listener and marks its own linked lost item RETURNED. Each user
   only writes their own documents, which is what the security rules allow.

8. **How do you decide what the item screen shows to whom?**
   `isOwner` compares the item's `ownerId` with the signed-in uid. The owner gets Edit, Delete,
   Mark returned and the claims list with Accept/Reject; anyone else gets "This is mine" on an
   open found item unless they already have a pending or accepted claim, in which case they see
   that claim with a Cancel button.

## Phase 6: Location and map

### Concepts used

- **Runtime permissions with the Activity Result API**: `rememberLauncherForActivityResult(
  RequestMultiplePermissions())` asks for fine and coarse together, so Android 12+ offers
  "Precise" or "Approximate". `shouldShowRequestPermissionRationale` decides between showing our
  rationale `AlertDialog` first and, after a permanent denial, an "Open settings" dialog. All of it
  lives in one reusable composable, `LocationPermissionGate`.
- **Fused Location Provider**: `getCurrentLocation` for a one-shot fix (wrapped in
  `withTimeoutOrNull`), and `requestLocationUpdates` turned into a `Flow` with `callbackFlow`;
  `awaitClose` removes the updates when the collector stops.
- **`DisposableEffect` with a lifecycle observer** on the Map tab: ON_START starts live location,
  ON_STOP and `onDispose` stop it, so the GPS runs only while the map is visible.
- **Geocoder, old and new API**: the listener-based calls on API 33+, the deprecated blocking
  calls on `Dispatchers.IO` below that, both behind `suspend` functions. `Geocoder.isPresent()`
  and empty results are handled by returning null / an empty list.
- **MapLibre Compose + OpenFreeMap** instead of Google Maps Compose (no API key, no billing).
  The map's sources and layers are composables declared inside `rememberMapState { }`: GeoJSON
  sources with `CircleLayer` dots for items and `FillLayer`/`LineLayer` polygons for zones.
- **Navigation results through `SavedStateHandle`**: the pin picker writes the chosen point into
  the previous back-stack entry's `savedStateHandle` and pops; the Post screen observes that key.
- **Location joined with local data**: `ZoneResolver` (nearest real zone containing a point) and
  "Near me" (feed sorted by haversine distance to each item's pin or zone centre).

### Key files to read first

1. `location/LocationClient.kt`: one-shot fix and the `callbackFlow` of updates.
2. `ui/components/PermissionGate.kt`: the whole permission flow in one place.
3. `location/ZoneResolver.kt` and `location/ItemPositions.kt`: small pure functions, unit-tested.
4. `ui/map/MapScreen.kt` and `MapViewModel.kt`: the map, layers, tap handling and live location.
5. `ui/map/PinPickerScreen.kt` and the `PinPicker` entries in `ui/navigation/MainNavHost.kt`.
6. `ui/debug/ZoneCaptureScreen.kt`: how the placeholder zone coordinates get fixed on campus.

### Questions an examiner might ask

1. **Why MapLibre and not Google Maps?**
   Google Maps needs an API key tied to a billing account. MapLibre is open source and
   OpenFreeMap serves free OpenStreetMap tiles with no key. The concepts (a map composable,
   camera, markers/layers, click handling) are the same as Google Maps Compose.

2. **What happens if the user picks "Approximate location"?**
   Coarse is still a grant, so everything works; the fix is only accurate to roughly a few
   hundred metres up to about 3 km. The app shows a snackbar saying the position may be off. Zone Capture
   refuses approximate location, because a zone centre must be precise.

3. **How do you know the user denied permission permanently?**
   After a denial, if `shouldShowRequestPermissionRationale` is false for both permissions, the
   system won't show its dialog again. Then the only way is the app's Settings page, which we
   open with `ACTION_APPLICATION_DETAILS_SETTINGS`.

4. **Why `callbackFlow` for location updates, and what does `awaitClose` do?**
   Play services delivers fixes through a callback. `callbackFlow` lets the callback `trySend`
   each fix into a Flow. `awaitClose` keeps the flow open until the collector is cancelled, then
   runs our cleanup (`removeLocationUpdates`), so updates can never leak.

5. **Why are placeholder zones never suggested?**
   They all sit at the made-up campus centre, so "you're in Block 34" would be a guess. Only
   zones captured on campus (`isPlaceholder = false`) can be suggested; all zones can still be
   picked by hand.

6. **How does the pin picker work, and how does the result get back?**
   The pin is fixed at the map's centre; dragging the map moves the spot under it, and a tap
   animates the camera there. "Use this spot" reads the camera centre, stores it as a
   `DoubleArray` in the Post entry's `SavedStateHandle` and pops. The Post screen observes that
   key, hands the point to its ViewModel once, then clears it.

7. **Why draw zones as polygons instead of circle layers?**
   A circle layer's radius is in screen pixels, so it would stay the same size while zooming.
   A polygon of 48 points around the real radius (in metres) grows and shrinks with the map.

8. **How does "Near me" sort the feed, and what if an item has no location?**
   One location fix when the chip is turned on (no live updates, so the list doesn't jump),
   then `FeedLogic` sorts by haversine distance to the item's pin, or its real zone's centre.
   Items with no position go last. A search ranking still wins over distance.

## Phase 7: Polish and migration

### Concepts used

- **Room migration**: database version 1 → 2 adds `items.color`. `MIGRATION_1_2` runs
  `ALTER TABLE items ADD COLUMN color TEXT`; Room then checks the result against the exported schema
  `2.json`. No destructive fallback, so a broken migration fails in testing, not on a phone.
- **`MigrationTestHelper`**: creates a real v1 database file from `1.json`, inserts a row with plain
  SQL, runs the migration and validates the schema; the second test opens the file with Room, the way
  the app does, and writes through the DAO.
- **Backward-compatible data**: the new column is nullable, old Firestore documents without `color`
  read as null, and an item without a colour embeds exactly the same text as before, so nothing had to
  be recomputed after the upgrade.
- **Responsive layout**: the window size class decides the layout. Under 600 dp a bottom bar, from
  600 dp a side rail, from 840 dp (expanded) a list-detail Home with the item open on the right. Both
  rules are plain functions with JVM tests.
- **Reusing a screen as a pane**: `ItemDetailRoute(itemId)` works as its own destination and inside
  Home's right pane; `viewModel(key = "item-$itemId", factory = …)` gives each item its own ViewModel.
- **Animations with a purpose**: the "Clear filters" chip slides in and out with `AnimatedVisibility`
  inside a `Row` (it expands horizontally by default).
- **Compose performance, measured**: the Compose compiler reports list which composables are
  skippable (all 100 here) and which parameters are unstable (only `NavHostController`).
- **Native libraries and ABIs**: an APK carries `.so` files per CPU type; `abiFilters` chooses which.
  A missing native dependency fails only at runtime (`UnsatisfiedLinkError`), which is why the Map tab
  had to be tried on a device.
- **Debug tooling for demos**: deterministic demo data (stable ids, dates relative to today) seeded
  through the normal repository path, so it syncs and matches like real posts.

### Key files to read first

1. `data/local/Migrations.kt`, `data/local/AppDatabase.kt` (version 2, `addMigrations`) and
   `androidTest/.../data/local/MigrationTest.kt`
2. `domain/matching/TextNormalizer.kt` (`forEmbedding` with colour) and `data/remote/mapper/RemoteMappers.kt`
3. `ui/home/HomeScreen.kt` (`HomeRoute` two-pane, `isTwoPaneWidth`, the Clear filters chip)
4. `ui/navigation/MainShell.kt` (bar only on tab screens)
5. `ui/debug/DemoData.kt` and `DebugToolsViewModel.onSeedDemo`
6. `app/build.gradle.kts` (`abiFilters`, the MapLibre runtime, the `composeReports` switch)

### Questions an examiner might ask

1. **How does your migration keep users' data?**
   `ALTER TABLE … ADD COLUMN` changes the table in place: every existing row stays and reads NULL for
   the new column. Room checks the migrated schema against `2.json` on open. The migration test proves
   it on a real v1 file with a row in it.

2. **Why no `fallbackToDestructiveMigration()`?**
   It would silently delete every local post (including unsynced ones) whenever a migration is
   missing. Without it, a missing migration crashes in testing, which is what we want to see first.

3. **Did adding colour force you to recompute all embeddings?**
   No. Blank parts are left out of the embedded text, so an item without a colour has exactly the same
   text as before. Items that do get a colour are re-embedded because editing clears the embedding,
   and sync treats a colour change as a text change.

4. **How does the app decide between one pane and two?**
   From the window width class: 840 dp and up is "expanded", so Home shows the list and the selected
   item side by side; below that, tapping an item navigates to its own screen. A phone in landscape
   is wide enough for two panes too.

5. **Why seed the demo data as two accounts?**
   Matching never pairs two posts by the same person, so a single account would show no matches. The
   found half is seeded as one user and the lost half as another; the five deliberate pairs then come
   out on top with the real model.

6. **How did you check performance rather than guess?**
   With the Compose compiler reports (`-PcomposeReports=true`): every restartable composable is
   skippable, and the only unstable parameter is the navigation controller, passed once. Lists use
   `key` and `contentType`, the FAB uses `derivedStateOf`, and UI state classes are `@Immutable`.

7. **What went wrong with the map, and how did you find it?**
   The map library needs a separate render-engine artifact on Android. Without it, the app built and
   all JVM tests passed, but opening the Map tab crashed with `UnsatisfiedLinkError`. Trying it on the
   emulator found it; the library's getting-started docs gave the missing `runtimeOnly` dependency.
