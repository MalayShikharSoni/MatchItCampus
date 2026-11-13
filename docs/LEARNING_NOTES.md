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
