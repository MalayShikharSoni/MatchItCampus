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
